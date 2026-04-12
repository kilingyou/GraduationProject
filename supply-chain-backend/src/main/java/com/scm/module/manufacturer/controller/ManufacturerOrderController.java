package com.scm.module.manufacturer.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scm.common.Constants;
import com.scm.common.PageResult;
import com.scm.common.Result;
import com.scm.integration.ipfs.IpfsStorageService;
import com.scm.module.manufacturer.dto.ManufacturerOrderVO;
import com.scm.module.manufacturer.entity.ManufacturingAgreement;
import com.scm.module.manufacturer.service.ManufacturerOrderAcceptanceService;
import com.scm.module.manufacturer.service.ManufacturerOrderViewService;
import com.scm.module.manufacturer.service.ManufacturingAgreementService;
import com.scm.module.supplier.entity.Bom;
import com.scm.module.supplier.entity.DesignDocument;
import com.scm.module.supplier.entity.ProductionRequest;
import com.scm.module.supplier.service.BomService;
import com.scm.module.supplier.service.DesignDocumentService;
import com.scm.module.supplier.service.ProductionRequestService;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/manufacturer/order")
@RequiredArgsConstructor
public class ManufacturerOrderController {

    private final ProductionRequestService productionRequestService;
    private final ManufacturingAgreementService agreementService;
    private final ManufacturerOrderViewService manufacturerOrderViewService;
    private final ManufacturerOrderAcceptanceService manufacturerOrderAcceptanceService;
    private final IpfsStorageService ipfsStorageService;
    private final DesignDocumentService designDocumentService;
    private final BomService bomService;

    /**
     * 订单大厅：待接单；我的订单：已签署协议的订单。
     *
     * @param scope pool | mine
     */
    @GetMapping("/list")
    public Result<PageResult<ManufacturerOrderVO>> listOrders(
            @RequestParam(defaultValue = "pool") String scope,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        LoginUser user = currentUser();
        if ("mine".equalsIgnoreCase(scope)) {
            return Result.ok(manufacturerOrderViewService.pageMyOrders(
                    user.getUserId(), page, pageSize, keyword, status));
        }
        return Result.ok(manufacturerOrderViewService.pageOrderPool(
                user.getUserId(), page, pageSize, keyword));
    }

    //接收订单功能
    @PostMapping(value = "/{orderId}/accept", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ManufacturingAgreement> acceptOrder(
            @PathVariable String orderId,
            @RequestParam BigDecimal finalPrice,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveryDate,
            @RequestPart("agreementFile") MultipartFile agreementFile)
            throws java.io.IOException {
        LoginUser user = currentUser();
        if (agreementFile == null || agreementFile.isEmpty()) {
            return Result.fail("请上传制造协议文件");
        }
        ManufacturingAgreement agreement = manufacturerOrderAcceptanceService.acceptOrder(
                orderId,
                user.getUserId(),
                user.getBlockchainAddr(),
                finalPrice,
                deliveryDate,
                agreementFile.getBytes(),
                agreementFile.getOriginalFilename());
        return Result.ok(agreement);
    }

    /**
     * 设计图纸下载：经 JWT 鉴权，从 IPFS 取流（不依赖浏览器直连网关）。
     * 可见范围：待接单且订单对当前制造商可见，或已接单且存在与该制造商的协议。
     */
    @GetMapping("/{orderId}/design-file")
    public ResponseEntity<byte[]> downloadDesignFile(@PathVariable String orderId) {
        LoginUser user = currentUser();
        ProductionRequest order = productionRequestService.getOne(
                new LambdaQueryWrapper<ProductionRequest>()
                        .eq(ProductionRequest::getOrderId, orderId));
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canManufacturerAccessDesignFile(order, user.getUserId())) {
            return ResponseEntity.status(403).build();
        }

        Long docId = order.getDesignDocId();
        if (docId == null && order.getBomId() != null) {
            Bom b = bomService.getById(order.getBomId());
            if (b != null) {
                docId = b.getDesignDocId();
            }
        }
        if (docId == null) {
            return ResponseEntity.notFound().build();
        }
        DesignDocument doc = designDocumentService.getById(docId);
        if (doc == null || !StringUtils.hasText(doc.getIpfsCid())) {
            return ResponseEntity.notFound().build();
        }
        byte[] data = ipfsStorageService.get(doc.getIpfsCid());
        if (data == null || data.length == 0) {
            return ResponseEntity.notFound().build();
        }

        String guessed = null;
        try {
            guessed = java.net.URLConnection.guessContentTypeFromStream(new ByteArrayInputStream(data));
        } catch (Exception ignore) {
        }
        MediaType mediaType = StringUtils.hasText(guessed)
                ? MediaType.parseMediaType(guessed)
                : MediaType.APPLICATION_OCTET_STREAM;
        String filename = StringUtils.hasText(doc.getFileName()) ? doc.getFileName() : "design-document";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return ResponseEntity.ok().headers(headers).body(data);
    }

    private boolean canManufacturerAccessDesignFile(ProductionRequest order, Long manufacturerId) {
        if (Constants.PENDING_ACCEPTANCE.equals(order.getStatus())) {
            // 广播单待接单阶段不开放全量图纸，避免未签约制造商批量获取设计资料
            if (order.getTargetManufacturer() == null) {
                return false;
            }
            return order.getTargetManufacturer().equals(manufacturerId);
        }
        long cnt = agreementService.count(
                new LambdaQueryWrapper<ManufacturingAgreement>()
                        .eq(ManufacturingAgreement::getOrderId, order.getOrderId())
                        .eq(ManufacturingAgreement::getManufacturerId, manufacturerId));
        return cnt > 0;
    }

    @GetMapping("/{orderId}/agreement")
    public Result<ManufacturingAgreement> viewAgreement(@PathVariable String orderId) {
        LoginUser user = currentUser();
        ManufacturingAgreement agreement = agreementService.getOne(
                new LambdaQueryWrapper<ManufacturingAgreement>()
                        .eq(ManufacturingAgreement::getOrderId, orderId)
                        .eq(ManufacturingAgreement::getManufacturerId, user.getUserId()));
        if (agreement == null) {
            return Result.fail("协议不存在或无权查看");
        }
        return Result.ok(agreement);
    }

    private LoginUser currentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
