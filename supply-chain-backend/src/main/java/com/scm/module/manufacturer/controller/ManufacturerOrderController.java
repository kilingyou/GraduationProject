package com.scm.module.manufacturer.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.scm.common.Constants;
import com.scm.common.PageResult;
import com.scm.common.Result;
import com.scm.common.util.HashUtil;
import com.scm.integration.blockchain.BlockchainAnchorService;
import com.scm.integration.evidence.EvidenceStorageService;
import com.scm.module.manufacturer.dto.ManufacturerOrderVO;
import com.scm.module.manufacturer.entity.ManufacturingAgreement;
import com.scm.module.manufacturer.service.ManufacturerOrderViewService;
import com.scm.module.manufacturer.service.ManufacturingAgreementService;
import com.scm.module.supplier.entity.ProductionRequest;
import com.scm.module.supplier.service.ProductionRequestService;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/manufacturer/order")
@RequiredArgsConstructor
public class ManufacturerOrderController {

    private final ProductionRequestService productionRequestService;
    private final ManufacturingAgreementService agreementService;
    private final ManufacturerOrderViewService manufacturerOrderViewService;
    private final EvidenceStorageService evidenceStorageService;
    private final BlockchainAnchorService blockchainAnchorService;

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

    @PostMapping(value = "/{orderId}/accept", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ManufacturingAgreement> acceptOrder(
            @PathVariable String orderId,
            @RequestParam BigDecimal finalPrice,
            @RequestParam LocalDate deliveryDate,
            @RequestPart(value = "agreementFile", required = false) MultipartFile agreementFile)
            throws java.io.IOException {
        LoginUser user = currentUser();

        ProductionRequest order = productionRequestService.getOne(
                new LambdaQueryWrapper<ProductionRequest>()
                        .eq(ProductionRequest::getOrderId, orderId));
        if (order == null) {
            return Result.fail("订单不存在");
        }
        if (!Constants.PENDING_ACCEPTANCE.equals(order.getStatus())) {
            return Result.fail("订单状态不允许接单");
        }
        if (order.getTargetManufacturer() != null
                && !order.getTargetManufacturer().equals(user.getUserId())) {
            return Result.fail("该订单已定向给其他制造商");
        }

        productionRequestService.update(new LambdaUpdateWrapper<ProductionRequest>()
                .eq(ProductionRequest::getOrderId, orderId)
                .set(ProductionRequest::getStatus, Constants.ACCEPTED));

        ManufacturingAgreement agreement = new ManufacturingAgreement();
        agreement.setOrderId(orderId);
        agreement.setManufacturerId(user.getUserId());
        agreement.setFinalPrice(finalPrice);
        agreement.setDeliveryDate(deliveryDate);

        String fileHashPart = "";
        if (agreementFile != null && !agreementFile.isEmpty()) {
            EvidenceStorageService.StoredEvidence ev = evidenceStorageService.store(
                    agreementFile.getBytes(),
                    agreementFile.getOriginalFilename(),
                    "MANUFACTURING_AGREEMENT_FILE");
            agreement.setAgreementHash(ev.getFileHash());
            agreement.setAgreementCid(ev.getIpfsCid());
            fileHashPart = ev.getFileHash();
        }
        String payload = orderId + "|" + user.getUserId() + "|" + finalPrice + "|" + deliveryDate + "|" + fileHashPart;
        agreement.setTxHash(blockchainAnchorService.anchor("MANUFACTURING_AGREEMENT", HashUtil.sha256Hex(payload)));

        if (StringUtils.hasText(user.getBlockchainAddr())) {
            agreement.setManufacturerSign("MANUFACTURER_ADDR:" + user.getBlockchainAddr());
        }

        agreementService.signAgreement(agreement);

        return Result.ok(agreement);
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
