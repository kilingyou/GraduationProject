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
import com.scm.module.supplier.entity.BomItem;
import com.scm.module.supplier.entity.ProductionRequest;
import com.scm.module.supplier.mapper.BomItemMapper;
import com.scm.module.supplier.service.ProductionRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductionBatchServiceImpl
        extends ServiceImpl<ProductionBatchMapper, ProductionBatch>
        implements ProductionBatchService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ProductionRequestService productionRequestService;
    private final ManufacturingAgreementService manufacturingAgreementService;
    private final BomItemMapper bomItemMapper;
    private final DeviceRecordMapper deviceRecordMapper;
    private final RejectRecordService rejectRecordService;
    private final BlockchainAnchorService blockchainAnchorService;
    private final SmartContractInvokeService smartContractInvokeService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductionBatch createBatch(String orderId, Long manufacturerId, Integer qty, Long bomItemId) {
        ProductionRequest order = productionRequestService.getOne(
                new LambdaQueryWrapper<ProductionRequest>().eq(ProductionRequest::getOrderId, orderId));
        //订单状态校验
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
        //校验制造协议
        ManufacturingAgreement agreement = manufacturingAgreementService.getOne(
                new LambdaQueryWrapper<ManufacturingAgreement>()
                        .eq(ManufacturingAgreement::getOrderId, orderId)
                        .eq(ManufacturingAgreement::getManufacturerId, manufacturerId));
        if (agreement == null) {
            throw new BusinessException("未找到本企业与该订单的制造协议");
        }

        int orderQty = order.getQuantity() == null ? 0 : order.getQuantity();
        Long orderBomId = order.getBomId();
        if (orderBomId != null) {
            long itemCount = bomItemMapper.selectCount(new LambdaQueryWrapper<BomItem>()
                    .eq(BomItem::getBomId, orderBomId));
            if (itemCount == 0) {
                throw new BusinessException("订单关联的 BOM 无明细行，请供应商维护 BOM 后再排产");
            }
            if (bomItemId == null) {
                throw new BusinessException("订单已关联 BOM，创建批次时必须选择 BOM 子件行");
            }
            BomItem item = bomItemMapper.selectById(bomItemId);
            if (item == null || !orderBomId.equals(item.getBomId())) {
                throw new BusinessException("BOM 明细行不存在或不属于本订单的 BOM");
            }
            int lineUse = item.getQuantity() == null || item.getQuantity() < 1 ? 1 : item.getQuantity();
            int lineCap = orderQty * lineUse;
            if (lineCap > 0) {
                List<ProductionBatch> lineBatches = list(new LambdaQueryWrapper<ProductionBatch>()
                        .eq(ProductionBatch::getOrderId, orderId)
                        .eq(ProductionBatch::getManufacturerId, manufacturerId)
                        .eq(ProductionBatch::getBomItemId, bomItemId));
                int sumPlannedLine = 0;
                for (ProductionBatch b : lineBatches) {
                    if (b.getPlannedQty() != null) {
                        sumPlannedLine += b.getPlannedQty();
                    }
                }
                if (sumPlannedLine + qty > lineCap) {
                    throw new BusinessException(
                            "该子件批次计划总量不能超过订单需求（订单套数 " + orderQty + " × 行用量 " + lineUse
                                    + " = " + lineCap + "，已计划 " + sumPlannedLine + "）");
                }
            }
        } else {
            if (bomItemId != null) {
                throw new BusinessException("订单未关联 BOM，无法指定 BOM 明细行");
            }
            if (orderQty > 0) {
                List<ProductionBatch> existingForQty = list(new LambdaQueryWrapper<ProductionBatch>()
                        .eq(ProductionBatch::getOrderId, orderId)
                        .eq(ProductionBatch::getManufacturerId, manufacturerId));
                int sumPlanned = 0;
                for (ProductionBatch b : existingForQty) {
                    if (b.getPlannedQty() != null) {
                        sumPlanned += b.getPlannedQty();
                    }
                }
                if (sumPlanned + qty > orderQty) {
                    throw new BusinessException(
                            "批次计划总量不能超过订单数量（订单 " + orderQty + "，已计划 " + sumPlanned + "）");
                }
            }
        }

        //更新订单状态为生产中
        Long existing = baseMapper.selectCount(new LambdaQueryWrapper<ProductionBatch>()
                .eq(ProductionBatch::getOrderId, orderId)
                .eq(ProductionBatch::getManufacturerId, manufacturerId));
        if (existing != null && existing == 0) {
            productionRequestService.update(new LambdaUpdateWrapper<ProductionRequest>()
                    .eq(ProductionRequest::getOrderId, orderId)
                    .set(ProductionRequest::getStatus, Constants.IN_PRODUCTION));
            smartContractInvokeService.updateProductionRequestStatus(orderId, Constants.IN_PRODUCTION);
        }
        //构造批次
        String batchId = "BATCH-" + LocalDate.now().format(DATE_FMT) + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        ProductionBatch batch = new ProductionBatch()
                .setBatchId(batchId)
                .setOrderId(orderId)
                .setManufacturerId(manufacturerId)
                .setBomItemId(bomItemId)
                .setPlannedQty(qty)
                .setCompletedQty(0)
                .setStatus("CREATED");
        //构造批次上链
        String payload = batchId + "|" + orderId + "|" + manufacturerId + "|" + qty + "|"
                + (bomItemId == null ? "" : bomItemId);
        batch.setTxHash(blockchainAnchorService.anchor("PRODUCTION_BATCH_CREATE", HashUtil.sha256Hex(payload)));
        //持久化构造批次
        save(batch);
        fillProductionBatchBomSummaries(java.util.Collections.singletonList(batch));
        return batch;
    }

    @Override
    public List<ProductionBatch> listByManufacturer(Long manufacturerId) {
        List<ProductionBatch> rows = list(new LambdaQueryWrapper<ProductionBatch>()
                .eq(ProductionBatch::getManufacturerId, manufacturerId)
                .orderByDesc(ProductionBatch::getCreateTime));
        fillProductionBatchBomSummaries(rows);
        return rows;
    }

    @Override
    public List<ProductionBatch> listByOrderId(String orderId) {
        if (orderId == null || orderId.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<ProductionBatch> rows = list(new LambdaQueryWrapper<ProductionBatch>()
                .eq(ProductionBatch::getOrderId, orderId)
                .orderByAsc(ProductionBatch::getCreateTime));
        fillProductionBatchBomSummaries(rows);
        return rows;
    }

    @Override
    public List<ProductionBatch> listByOrderIdAndManufacturer(String orderId, Long manufacturerId) {
        if (orderId == null || orderId.trim().isEmpty() || manufacturerId == null) {
            return java.util.Collections.emptyList();
        }
        List<ProductionBatch> rows = list(new LambdaQueryWrapper<ProductionBatch>()
                .eq(ProductionBatch::getOrderId, orderId.trim())
                .eq(ProductionBatch::getManufacturerId, manufacturerId)
                .orderByAsc(ProductionBatch::getCreateTime));
        fillProductionBatchBomSummaries(rows);
        return rows;
    }

    @Override
    public IPage<ProductionBatch> pageByManufacturer(Long manufacturerId, Page<ProductionBatch> page, String orderId) {
        // 注意：.eq(cond, col, val) 的 val 会先求值，orderId 为 null 时不能写 orderId.trim()
        String orderIdEq = StringUtils.hasText(orderId) ? orderId.trim() : null;
        LambdaQueryWrapper<ProductionBatch> w = new LambdaQueryWrapper<ProductionBatch>()
                .eq(ProductionBatch::getManufacturerId, manufacturerId)
                .eq(orderIdEq != null, ProductionBatch::getOrderId, orderIdEq)
                .orderByDesc(ProductionBatch::getCreateTime);
        IPage<ProductionBatch> raw = page(page, w);
        fillProductionBatchBomSummaries(raw.getRecords());
        return raw;
    }

    private void fillProductionBatchBomSummaries(List<ProductionBatch> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> ids = rows.stream()
                .map(ProductionBatch::getBomItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return;
        }
        List<BomItem> items = bomItemMapper.selectBatchIds(ids);
        Map<Long, BomItem> byId = items.stream().collect(Collectors.toMap(BomItem::getId, x -> x, (a, b) -> a));
        for (ProductionBatch b : rows) {
            if (b.getBomItemId() == null) {
                continue;
            }
            BomItem it = byId.get(b.getBomItemId());
            if (it != null) {
                b.setBomPartSummary(summarizeBomItem(it));
            }
        }
    }

    static String summarizeBomItem(BomItem it) {
        String num = it.getPartNumber() != null ? it.getPartNumber().trim() : "";
        String name = it.getPartName() != null ? it.getPartName().trim() : "";
        if (StringUtils.hasText(num) && StringUtils.hasText(name)) {
            return num + " / " + name;
        }
        return StringUtils.hasText(num) ? num : name;
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
            smartContractInvokeService.updateProductionRequestStatus(batch.getOrderId(), Constants.COMPLETED);
        }
    }
}
