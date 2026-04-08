package com.scm.module.manufacturer.service.impl;

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
import com.scm.module.manufacturer.entity.DeviceRecord;
import com.scm.module.manufacturer.entity.ManufacturingAgreement;
import com.scm.module.manufacturer.entity.ProductionBatch;
import com.scm.module.manufacturer.entity.RejectRecord;
import com.scm.module.manufacturer.mapper.DeviceRecordMapper;
import com.scm.module.manufacturer.mapper.ProductionBatchMapper;
import com.scm.module.manufacturer.service.ManufacturingAgreementService;
import com.scm.module.manufacturer.service.ProductionBatchService;
import com.scm.module.manufacturer.service.RejectRecordService;
import com.scm.module.supplier.entity.ProductionRequest;
import com.scm.module.supplier.service.ProductionRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductionBatchServiceImpl
        extends ServiceImpl<ProductionBatchMapper, ProductionBatch>
        implements ProductionBatchService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ProductionRequestService productionRequestService;
    private final ManufacturingAgreementService manufacturingAgreementService;
    private final DeviceRecordMapper deviceRecordMapper;
    private final RejectRecordService rejectRecordService;
    private final BlockchainAnchorService blockchainAnchorService;
    private final SmartContractInvokeService smartContractInvokeService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductionBatch createBatch(String orderId, Long manufacturerId, Integer qty) {
        ProductionRequest order = productionRequestService.getOne(
                new LambdaQueryWrapper<ProductionRequest>().eq(ProductionRequest::getOrderId, orderId));
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (Constants.CANCELLED.equals(order.getStatus())) {
            throw new BusinessException("订单已撤销，无法创建批次");
        }
        if (Constants.COMPLETED.equals(order.getStatus())) {
            throw new BusinessException("订单已完工，无法创建批次");
        }
        if (!Constants.ACCEPTED.equals(order.getStatus()) && !Constants.IN_PRODUCTION.equals(order.getStatus())) {
            throw new BusinessException("订单须为已接单或生产中才可创建生产批次");
        }
        ManufacturingAgreement agreement = manufacturingAgreementService.getOne(
                new LambdaQueryWrapper<ManufacturingAgreement>()
                        .eq(ManufacturingAgreement::getOrderId, orderId)
                        .eq(ManufacturingAgreement::getManufacturerId, manufacturerId));
        if (agreement == null) {
            throw new BusinessException("未找到本企业与该订单的制造协议");
        }

        Long existing = baseMapper.selectCount(new LambdaQueryWrapper<ProductionBatch>()
                .eq(ProductionBatch::getOrderId, orderId)
                .eq(ProductionBatch::getManufacturerId, manufacturerId));
        if (existing != null && existing == 0) {
            productionRequestService.update(new LambdaUpdateWrapper<ProductionRequest>()
                    .eq(ProductionRequest::getOrderId, orderId)
                    .set(ProductionRequest::getStatus, Constants.IN_PRODUCTION));
        }

        String batchId = "BATCH-" + LocalDate.now().format(DATE_FMT) + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        ProductionBatch batch = new ProductionBatch()
                .setBatchId(batchId)
                .setOrderId(orderId)
                .setManufacturerId(manufacturerId)
                .setPlannedQty(qty)
                .setCompletedQty(0)
                .setStatus("CREATED");
        String payload = batchId + "|" + orderId + "|" + manufacturerId + "|" + qty;
        batch.setTxHash(blockchainAnchorService.anchor("PRODUCTION_BATCH_CREATE", HashUtil.sha256Hex(payload)));
        save(batch);
        return batch;
    }

    @Override
    public List<ProductionBatch> listByManufacturer(Long manufacturerId) {
        return list(new LambdaQueryWrapper<ProductionBatch>()
                .eq(ProductionBatch::getManufacturerId, manufacturerId)
                .orderByDesc(ProductionBatch::getCreateTime));
    }

    @Override
    public List<ProductionBatch> listByOrderId(String orderId) {
        if (orderId == null || orderId.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return list(new LambdaQueryWrapper<ProductionBatch>()
                .eq(ProductionBatch::getOrderId, orderId)
                .orderByAsc(ProductionBatch::getCreateTime));
    }

    @Override
    public IPage<ProductionBatch> pageByManufacturer(Long manufacturerId, Page<ProductionBatch> page) {
        LambdaQueryWrapper<ProductionBatch> w = new LambdaQueryWrapper<ProductionBatch>()
                .eq(ProductionBatch::getManufacturerId, manufacturerId)
                .orderByDesc(ProductionBatch::getCreateTime);
        return page(page, w);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshCompletedQtyFromDevices(String batchId) {
        if (!StringUtils.hasText(batchId)) {
            return;
        }
        ProductionBatch batch = getOne(new LambdaQueryWrapper<ProductionBatch>()
                .eq(ProductionBatch::getBatchId, batchId.trim()));
        if (batch == null || "COMPLETED".equals(batch.getStatus())) {
            return;
        }
        Long cnt = deviceRecordMapper.selectCount(
                new LambdaQueryWrapper<DeviceRecord>().eq(DeviceRecord::getBatchId, batchId.trim()));
        int deviceCount = cnt == null ? 0 : cnt.intValue();
        int q = deviceCount;
        if (batch.getPlannedQty() != null && batch.getPlannedQty() > 0) {
            q = Math.min(deviceCount, batch.getPlannedQty());
        }
        update(new LambdaUpdateWrapper<ProductionBatch>()
                .eq(ProductionBatch::getBatchId, batchId.trim())
                .set(ProductionBatch::getCompletedQty, q));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeBatch(String batchId, Long manufacturerId) {
        ProductionBatch batch = getOne(new LambdaQueryWrapper<ProductionBatch>()
                .eq(ProductionBatch::getBatchId, batchId));
        if (batch == null || !manufacturerId.equals(batch.getManufacturerId())) {
            throw new BusinessException("批次不存在或无权限");
        }
        if ("COMPLETED".equals(batch.getStatus())) {
            return;
        }
        List<DeviceRecord> devices = deviceRecordMapper.selectList(
                new LambdaQueryWrapper<DeviceRecord>().eq(DeviceRecord::getBatchId, batchId));
        if (devices.isEmpty()) {
            throw new BusinessException("批次下无设备，无法完工");
        }
        if (batch.getPlannedQty() != null && devices.size() < batch.getPlannedQty()) {
            throw new BusinessException("已生成 ECID 数量少于计划数量，请先补足生产数量再完工");
        }
        for (DeviceRecord d : devices) {
            assertDeviceReadyForBatchClose(d, manufacturerId);
        }
        finalizeBatchAndMaybeCompleteOrder(batch, manufacturerId, devices);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void tryAutoCompleteBatch(String batchId, Long manufacturerId) {
        ProductionBatch batch = getOne(new LambdaQueryWrapper<ProductionBatch>()
                .eq(ProductionBatch::getBatchId, batchId));
        if (!isBatchEligibleForAutoCompletion(batch, manufacturerId)) {
            return;
        }
        List<DeviceRecord> devices = deviceRecordMapper.selectList(
                new LambdaQueryWrapper<DeviceRecord>().eq(DeviceRecord::getBatchId, batchId));
        if (!isDeviceListSatisfied(batch, devices, manufacturerId)) {
            return;
        }
        finalizeBatchAndMaybeCompleteOrder(batch, manufacturerId, devices);
    }

    private boolean isBatchEligibleForAutoCompletion(ProductionBatch batch, Long manufacturerId) {
        return batch != null
                && manufacturerId.equals(batch.getManufacturerId())
                && !"COMPLETED".equals(batch.getStatus());
    }

    /**
     * 与 completeBatch 中设备条件一致（不抛异常，供自动完工判断）。
     * 合格品：QC_PASS 且已上链。不合格品：REJECTED 且退货/销毁处置已完结并有链上完结凭证。
     */
    private boolean isDeviceListSatisfied(ProductionBatch batch, List<DeviceRecord> devices, Long manufacturerId) {
        if (devices.isEmpty()) {
            return false;
        }
        if (batch.getPlannedQty() != null && devices.size() < batch.getPlannedQty()) {
            return false;
        }
        for (DeviceRecord d : devices) {
            if (!isDeviceReadyForBatchClose(d, manufacturerId)) {
                return false;
            }
        }
        return true;
    }

    private void assertDeviceReadyForBatchClose(DeviceRecord d, Long manufacturerId) {
        if (Constants.QC_PASS.equals(d.getStatus())) {
            if (d.getChainRegistered() == null || d.getChainRegistered() != 1) {
                throw new BusinessException("存在未上链的 ECID: " + d.getEcid());
            }
            return;
        }
        if (Constants.REJECTED.equals(d.getStatus())) {
            RejectRecord rr = findLatestCompletedDisposal(d.getEcid(), manufacturerId);
            if (rr == null) {
                throw new BusinessException("不合格设备尚未完成退货/销毁闭环，无法批次完工: " + d.getEcid());
            }
            if (!hasDisposalOnChainEvidence(d, rr)) {
                throw new BusinessException("不合格设备处置完结尚未上链归档，无法批次完工: " + d.getEcid());
            }
            return;
        }
        throw new BusinessException("存在未质检合格的 ECID: " + d.getEcid());
    }

    private boolean isDeviceReadyForBatchClose(DeviceRecord d, Long manufacturerId) {
        if (Constants.QC_PASS.equals(d.getStatus())) {
            return d.getChainRegistered() != null && d.getChainRegistered() == 1;
        }
        if (Constants.REJECTED.equals(d.getStatus())) {
            RejectRecord rr = findLatestCompletedDisposal(d.getEcid(), manufacturerId);
            return rr != null && hasDisposalOnChainEvidence(d, rr);
        }
        return false;
    }

    private RejectRecord findLatestCompletedDisposal(String ecid, Long manufacturerId) {
        if (!StringUtils.hasText(ecid) || manufacturerId == null) {
            return null;
        }
        return rejectRecordService.getOne(
                new LambdaQueryWrapper<RejectRecord>()
                        .eq(RejectRecord::getEcid, ecid)
                        .eq(RejectRecord::getManufacturerId, manufacturerId)
                        .eq(RejectRecord::getDisposalStatus, Constants.DISPOSAL_COMPLETED)
                        .orderByDesc(RejectRecord::getId)
                        .last("LIMIT 1"));
    }

    /** 设备表已标记上链，或不合格记录上存有处置完结锚定哈希（兼容历史数据） */
    private boolean hasDisposalOnChainEvidence(DeviceRecord d, RejectRecord rr) {
        if (d.getChainRegistered() != null && d.getChainRegistered() == 1) {
            return true;
        }
        return rr != null && StringUtils.hasText(rr.getDisposalCompleteTxHash());
    }

    private void finalizeBatchAndMaybeCompleteOrder(ProductionBatch batch, Long manufacturerId,
                                                    List<DeviceRecord> devices) {
        batch.setStatus("COMPLETED");
        batch.setCompletedQty(devices.size());
        updateById(batch);

        List<ProductionBatch> allForOrder = list(new LambdaQueryWrapper<ProductionBatch>()
                .eq(ProductionBatch::getOrderId, batch.getOrderId())
                .eq(ProductionBatch::getManufacturerId, manufacturerId));
        boolean allDone = true;
        for (ProductionBatch b : allForOrder) {
            if (!"COMPLETED".equals(b.getStatus())) {
                allDone = false;
                break;
            }
        }
        if (allDone) {
            productionRequestService.update(new LambdaUpdateWrapper<ProductionRequest>()
                    .eq(ProductionRequest::getOrderId, batch.getOrderId())
                    .set(ProductionRequest::getStatus, Constants.COMPLETED));
            blockchainAnchorService.anchor("PRODUCTION_ORDER_COMPLETE",
                    HashUtil.sha256Hex(batch.getOrderId() + "|" + manufacturerId));
            smartContractInvokeService.recordProductionComplete(
                    batch.getOrderId(),
                    batch.getBatchId(),
                    true,
                    "",
                    "AUTO_COMPLETE"
            );
        }
    }
}
