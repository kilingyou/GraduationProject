package com.scm.module.supplier.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scm.common.Constants;
import com.scm.common.exception.BusinessException;
import com.scm.common.util.HashUtil;
import com.scm.integration.blockchain.BlockchainAnchorService;
import com.scm.integration.blockchain.SmartContractInvokeService;
import com.scm.module.supplier.entity.Bom;
import com.scm.module.supplier.entity.DesignDocument;
import com.scm.module.supplier.entity.ProductionRequest;
import com.scm.module.supplier.mapper.ProductionRequestMapper;
import com.scm.module.supplier.service.BomService;
import com.scm.module.supplier.service.DesignDocumentService;
import com.scm.module.supplier.service.ProductionRequestService;
import com.scm.module.system.entity.SysSupplierAudit;
import com.scm.module.system.mapper.SysSupplierAuditMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionRequestServiceImpl extends ServiceImpl<ProductionRequestMapper, ProductionRequest>
        implements ProductionRequestService {

    private final BlockchainAnchorService blockchainAnchorService;
    private final SmartContractInvokeService smartContractInvokeService;
    private final SysSupplierAuditMapper sysSupplierAuditMapper;
    private final BomService bomService;
    private final DesignDocumentService designDocumentService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductionRequest createOrder(ProductionRequest request) {
        // PDF: 供应商资质审核通过后，才允许发布生产订单
        SysSupplierAudit audit = sysSupplierAuditMapper.selectOne(new LambdaQueryWrapper<SysSupplierAudit>()
                .eq(SysSupplierAudit::getUserId, request.getSupplierId())
                .orderByDesc(SysSupplierAudit::getCreateTime)
                .last("LIMIT 1"));
        if (audit == null || !"APPROVED".equalsIgnoreCase(audit.getAuditStatus())) {
            throw new BusinessException("供应商资质未审核通过，无法发布生产订单");
        }

        if (request.getBomId() == null) {
            throw new BusinessException("请选择 BOM");
        }
        Bom bom = bomService.getById(request.getBomId());
        if (bom == null || !request.getSupplierId().equals(bom.getSupplierId())) {
            throw new BusinessException("BOM 不存在或无权使用");
        }
        request.setDesignDocId(bom.getDesignDocId());
        if (bom.getDesignDocId() != null) {
            DesignDocument doc = designDocumentService.getById(bom.getDesignDocId());
            if (doc != null) {
                request.setDesignDocHash(doc.getFileHash());
            }
        }

        request.setOrderId(UUID.randomUUID().toString().replace("-", ""));
        request.setStatus("PENDING_ACCEPTANCE");
        save(request);

        String anchorPayload = request.getOrderId() + "|" + request.getSupplierId();
        //通用锚定上链“PRODUCTION_ORDER”+生产订单id和供应商账户id的哈希
        request.setTxHash(blockchainAnchorService.anchor("PRODUCTION_ORDER", HashUtil.sha256Hex(anchorPayload)));
        //创建生产订单
        smartContractInvokeService.createProductionRequest(
                request.getOrderId(),
                request.getTargetManufacturer(),
                bom.getFileHash(),
                request.getQuantity(),
                request.getDesignDocHash(),
                request.getExpectedDelivery(),
                request.getQualityRequirement()
        );
        updateById(request);

        log.info("Production order created: id={}, orderId={}", request.getId(), request.getOrderId());
        return request;
    }

    @Override
    public IPage<ProductionRequest> listBySupplier(Long supplierId, Page<ProductionRequest> page, String status) {
        LambdaQueryWrapper<ProductionRequest> wrapper = new LambdaQueryWrapper<ProductionRequest>()
                .eq(ProductionRequest::getSupplierId, supplierId)
                .eq(StringUtils.hasText(status), ProductionRequest::getStatus, status)
                .orderByDesc(ProductionRequest::getCreateTime);
        return page(page, wrapper);
    }

    @Override
    public IPage<ProductionRequest> listForManufacturer(Long manufacturerId, Page<ProductionRequest> page) {
        LambdaQueryWrapper<ProductionRequest> wrapper = new LambdaQueryWrapper<ProductionRequest>()
                .and(w -> w.eq(ProductionRequest::getTargetManufacturer, manufacturerId)
                        .or()
                        .isNull(ProductionRequest::getTargetManufacturer))
                .orderByDesc(ProductionRequest::getCreateTime);
        return page(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrderBySupplier(Long orderDbId, Long supplierId) {
        ProductionRequest order = getById(orderDbId);
        if (order == null || !supplierId.equals(order.getSupplierId())) {
            throw new BusinessException("订单不存在");
        }
        if (!Constants.PENDING_ACCEPTANCE.equals(order.getStatus())) {
            throw new BusinessException("仅待接单状态的订单可撤销");
        }
        String payload = order.getOrderId() + "|" + supplierId + "|CANCELLED";
        blockchainAnchorService.anchor("PRODUCTION_ORDER_CANCEL", HashUtil.sha256Hex(payload));
        update(new LambdaUpdateWrapper<ProductionRequest>()
                .eq(ProductionRequest::getId, orderDbId)
                .set(ProductionRequest::getStatus, Constants.CANCELLED));
        log.info("Production order cancelled by supplier: id={}, orderId={}", orderDbId, order.getOrderId());
    }
}
