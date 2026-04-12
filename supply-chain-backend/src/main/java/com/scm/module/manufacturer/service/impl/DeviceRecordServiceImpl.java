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
import com.scm.module.manufacturer.dto.DeviceRegisterRequest;
import com.scm.module.manufacturer.entity.DeviceRecord;
import com.scm.module.manufacturer.entity.ProductionBatch;
import com.scm.module.manufacturer.mapper.DeviceRecordMapper;
import com.scm.module.manufacturer.service.DeviceRecordService;
import com.scm.module.manufacturer.service.ProductionBatchService;
import com.scm.module.supplier.entity.BomItem;
import com.scm.module.supplier.entity.ProductionRequest;
import com.scm.module.supplier.mapper.BomItemMapper;
import com.scm.module.supplier.service.ProductionRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeviceRecordServiceImpl
        extends ServiceImpl<DeviceRecordMapper, DeviceRecord>
        implements DeviceRecordService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ProductionBatchService productionBatchService;
    private final ProductionRequestService productionRequestService;
    private final BomItemMapper bomItemMapper;
    private final BlockchainAnchorService blockchainAnchorService;
    private final SmartContractInvokeService smartContractInvokeService;

    //批量生产ECID
    @Override
    public List<String> generateEcids(String batchId, String orderId, Long manufacturerId, Integer qty, String deviceType) {
        ProductionBatch batch = productionBatchService.getOne(
                new LambdaQueryWrapper<ProductionBatch>().eq(ProductionBatch::getBatchId, batchId));
        if (batch == null || !manufacturerId.equals(batch.getManufacturerId())) {
            throw new BusinessException("批次不存在或无权限");
        }
        if (!batch.getOrderId().equals(orderId)) {
            throw new BusinessException("订单号与批次不匹配");
        }
        ProductionRequest order = productionRequestService.getOne(
                new LambdaQueryWrapper<ProductionRequest>().eq(ProductionRequest::getOrderId, orderId));
        Long bomItemIdForRecord = batch.getBomItemId();
        String resolvedDeviceType = deviceType;

        if (order != null && order.getBomId() != null) {
            if (bomItemIdForRecord == null) {
                throw new BusinessException("该批次未绑定 BOM 子件行，请新建「子件批次」后再生成 ECID");
            }
            BomItem item = bomItemMapper.selectById(bomItemIdForRecord);
            if (item == null || !order.getBomId().equals(item.getBomId())) {
                throw new BusinessException("BOM 明细行无效");
            }
            int orderQty = order.getQuantity() == null ? 0 : order.getQuantity();
            int lineUse = item.getQuantity() == null || item.getQuantity() < 1 ? 1 : item.getQuantity();
            int lineCap = orderQty * lineUse;
            if (lineCap > 0) {
                long lineExisting = count(new LambdaQueryWrapper<DeviceRecord>()
                        .eq(DeviceRecord::getOrderId, orderId)
                        .eq(DeviceRecord::getManufacturerId, manufacturerId)
                        .eq(DeviceRecord::getBomItemId, bomItemIdForRecord));
                if (lineExisting + qty > lineCap) {
                    throw new BusinessException(
                            "该子件 ECID 数量将超过订单需求（上限 " + lineCap + "，已有 " + lineExisting + "）");
                }
            }
            resolvedDeviceType = deviceTypeFromBomItem(item);
        } else {
            bomItemIdForRecord = null;
            if (!StringUtils.hasText(deviceType)) {
                throw new BusinessException("请输入设备类型");
            }
            resolvedDeviceType = deviceType.trim();
        }

        if (batch.getPlannedQty() != null && batch.getPlannedQty() > 0) {
            long existing = count(new LambdaQueryWrapper<DeviceRecord>()
                    .eq(DeviceRecord::getBatchId, batchId));
            if (existing + qty > batch.getPlannedQty()) {
                throw new BusinessException(
                        "本批次设备数量将超过计划数量（计划 " + batch.getPlannedQty() + "，已有 " + existing + "）");
            }
        }
        String dateStr = LocalDate.now().format(DATE_FMT);
        String mfCode = "M" + String.format("%04d", manufacturerId % 10000);
        // ECID 序号按「制造商 + 当天日期」全局递增，不能仅按批次计数，否则多批次同日会重复
        String ecidPrefix = "ECID-" + mfCode + "-" + dateStr + "-";
        int maxSeq = list(new LambdaQueryWrapper<DeviceRecord>()
                .eq(DeviceRecord::getManufacturerId, manufacturerId)
                .likeRight(DeviceRecord::getEcid, ecidPrefix))
                .stream()
                .mapToInt(r -> parseEcidSeq(r.getEcid(), ecidPrefix))
                .max()
                .orElse(0);
        int startSeq = maxSeq + 1;

        List<DeviceRecord> records = new ArrayList<>(qty);
        List<String> ecids = new ArrayList<>(qty);
        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < qty; i++) {
            String seq = String.format("%06d", startSeq + i);
            String ecid = "ECID-" + mfCode + "-" + dateStr + "-" + seq;
            ecids.add(ecid);

            DeviceRecord record = new DeviceRecord()
                    .setEcid(ecid)
                    .setOrderId(orderId)
                    .setBatchId(batchId)
                    .setManufacturerId(manufacturerId)
                    .setBomItemId(bomItemIdForRecord)
                    .setDeviceType(resolvedDeviceType)
                    .setManufactureTime(now)
                    .setStatus("PRODUCED")
                    .setChainRegistered(0)
                    .setReleasedToAssembler(0);
            records.add(record);
        }
        //持久化设备记录列表
        saveBatch(records);
        productionBatchService.refreshCompletedQtyFromDevices(batchId);
        return ecids;
    }

    private static int parseEcidSeq(String ecid, String prefix) {
        if (ecid == null || !ecid.startsWith(prefix)) {
            return 0;
        }
        try {
            return Integer.parseInt(ecid.substring(prefix.length()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    //批量生产ECID
    @Override
    public List<String> generateEcidsForBatch(String batchId, Long manufacturerId, Integer qty, String deviceType) {
        ProductionBatch batch = productionBatchService.getOne(
                new LambdaQueryWrapper<ProductionBatch>().eq(ProductionBatch::getBatchId, batchId));
        if (batch == null || !manufacturerId.equals(batch.getManufacturerId())) {
            throw new BusinessException("批次不存在或无权限");
        }
        return generateEcids(batchId, batch.getOrderId(), manufacturerId, qty, deviceType);
    }

    @Override
    public List<DeviceRecord> listByBatch(String batchId) {
        return list(new LambdaQueryWrapper<DeviceRecord>()
                .eq(DeviceRecord::getBatchId, batchId)
                .orderByAsc(DeviceRecord::getEcid));
    }

    @Override
    public IPage<DeviceRecord> pageForManufacturer(Long manufacturerId, Page<DeviceRecord> page, String batchId) {
        LambdaQueryWrapper<DeviceRecord> w = new LambdaQueryWrapper<DeviceRecord>()
                .eq(DeviceRecord::getManufacturerId, manufacturerId)
                .eq(StringUtils.hasText(batchId), DeviceRecord::getBatchId, batchId)
                .orderByDesc(DeviceRecord::getCreateTime);
        IPage<DeviceRecord> raw = page(page, w);
        fillDeviceBomSummaries(raw.getRecords());
        return raw;
    }

    private void fillDeviceBomSummaries(List<DeviceRecord> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> ids = rows.stream()
                .map(DeviceRecord::getBomItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return;
        }
        List<BomItem> items = bomItemMapper.selectBatchIds(ids);
        Map<Long, BomItem> byId = items.stream().collect(Collectors.toMap(BomItem::getId, x -> x, (a, b) -> a));
        for (DeviceRecord r : rows) {
            if (r.getBomItemId() == null) {
                continue;
            }
            BomItem it = byId.get(r.getBomItemId());
            if (it != null) {
                r.setBomPartSummary(summarizeBomItem(it));
            }
        }
    }

    private static String summarizeBomItem(BomItem it) {
        String num = it.getPartNumber() != null ? it.getPartNumber().trim() : "";
        String name = it.getPartName() != null ? it.getPartName().trim() : "";
        if (StringUtils.hasText(num) && StringUtils.hasText(name)) {
            return num + " / " + name;
        }
        return StringUtils.hasText(num) ? num : name;
    }

    /** 写入链上 devType 与库展示：料号 + 名称（与 BOM 行一致） */
    private static String deviceTypeFromBomItem(BomItem it) {
        String num = it.getPartNumber() != null ? it.getPartNumber().trim() : "";
        String name = it.getPartName() != null ? it.getPartName().trim() : "";
        if (StringUtils.hasText(num) && StringUtils.hasText(name)) {
            return num + " " + name;
        }
        return StringUtils.hasText(num) ? num : (StringUtils.hasText(name) ? name : "BOM_ITEM_" + it.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean registerOnChain(List<Long> ids) {
        //查询设备记录列表
        List<DeviceRecord> records = listByIds(ids);
        //如果设备记录状态为rejected则收集ECID并抛异常
        List<String> rejectedEcids = records.stream()
                .filter(r -> Constants.REJECTED.equals(r.getStatus()))
                .map(DeviceRecord::getEcid)
                .collect(Collectors.toList());
        if (!rejectedEcids.isEmpty()) {
            throw new BusinessException(
                    "质检不合格的设备不能上链注册，请先处理或取消勾选：" + String.join("、", rejectedEcids));
        }
        for (DeviceRecord record : records) {
            if (!Constants.QC_PASS.equals(record.getStatus())) {
                throw new BusinessException("仅质检合格（QC_PASS）的设备可上链注册，请先完成质检: " + record.getEcid());
            }
            if (!StringUtils.hasText(record.getTestReportHash())) {
                throw new BusinessException("设备未绑定质检报告哈希，请先上传检测报告后再注册: " + record.getEcid());
            }
            if (record.getChainRegistered() != null && record.getChainRegistered() == 1) {
                throw new BusinessException("设备已上链注册，请勿重复提交: " + record.getEcid());
            }
        }
        for (DeviceRecord record : records) {
            String payload = record.getEcid() + "|" + record.getOrderId() + "|"
                    + record.getBatchId() + "|" + record.getManufacturerId() + "|"
                    + (record.getBomItemId() == null ? "" : record.getBomItemId());
            String txHash = blockchainAnchorService.anchor(
                    "DEVICE_REGISTER", HashUtil.sha256Hex(payload));
            smartContractInvokeService.registerDeviceRecord(
                    record.getEcid(),
                    record.getOrderId(),
                    record.getBatchId(),
                    record.getDeviceType(),
                    record.getTestReportHash(),
                    Constants.QC_PASS
            );
            //设置订单记录为已注册，并设置交易哈希
            record.setChainRegistered(1);
            record.setTxHash(txHash);
            if (!Constants.QC_PASS.equals(record.getStatus())) {
                record.setStatus(Constants.QC_PASS);
            }
        }
        //更新数据库记录
        boolean ok = updateBatchById(records);
        if (ok) {
            Map<String, Long> batchToManufacturer = new LinkedHashMap<>();
            for (DeviceRecord r : records) {
                if (StringUtils.hasText(r.getBatchId())) {
                    batchToManufacturer.putIfAbsent(r.getBatchId(), r.getManufacturerId());
                }
            }
            for (Map.Entry<String, Long> e : batchToManufacturer.entrySet()) {
                productionBatchService.tryAutoCompleteBatch(e.getKey(), e.getValue());
            }
        }
        return ok;
    }

    //校验id
    @Override
    public boolean registerOnChain(DeviceRegisterRequest request, Long manufacturerId) {
        List<Long> ids = request.getIds();
        if (ids == null || ids.isEmpty()) {
            if (request.getEcids() == null || request.getEcids().isEmpty()) {
                return false;
            }
            List<DeviceRecord> recs = list(new LambdaQueryWrapper<DeviceRecord>()
                    .in(DeviceRecord::getEcid, request.getEcids())
                    .eq(DeviceRecord::getManufacturerId, manufacturerId));
            ids = recs.stream().map(DeviceRecord::getId).collect(Collectors.toList());
            if (ids.isEmpty()) {
                throw new BusinessException("未找到可注册的设备");
            }
        } else {
            List<DeviceRecord> recs = listByIds(ids);
            for (DeviceRecord r : recs) {
                if (!manufacturerId.equals(r.getManufacturerId())) {
                    throw new BusinessException("存在无权限的设备记录");
                }
            }
        }
        return registerOnChain(ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int releasePartsToAssemblerByEcids(List<String> ecids, Long manufacturerId) {
        if (ecids == null || ecids.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (String raw : ecids) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            DeviceRecord d = getOne(new LambdaQueryWrapper<DeviceRecord>()
                    .eq(DeviceRecord::getEcid, raw.trim()));
            if (!canReleaseToAssembler(d, manufacturerId)) {
                continue;
            }
            update(new LambdaUpdateWrapper<DeviceRecord>()
                    .eq(DeviceRecord::getId, d.getId())
                    .set(DeviceRecord::getReleasedToAssembler, 1));
            n++;
        }
        return n;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int releasePartsToAssemblerByBatch(String batchId, Long manufacturerId) {
        if (!StringUtils.hasText(batchId)) {
            return 0;
        }
        List<DeviceRecord> rows = list(new LambdaQueryWrapper<DeviceRecord>()
                .eq(DeviceRecord::getBatchId, batchId.trim())
                .eq(DeviceRecord::getManufacturerId, manufacturerId));
        int n = 0;
        for (DeviceRecord d : rows) {
            if (!canReleaseToAssembler(d, manufacturerId)) {
                continue;
            }
            update(new LambdaUpdateWrapper<DeviceRecord>()
                    .eq(DeviceRecord::getId, d.getId())
                    .set(DeviceRecord::getReleasedToAssembler, 1));
            n++;
        }
        return n;
    }

    private static boolean canReleaseToAssembler(DeviceRecord d, Long manufacturerId) {
        if (d == null || manufacturerId == null || !manufacturerId.equals(d.getManufacturerId())) {
            return false;
        }
        if (!Constants.QC_PASS.equals(d.getStatus())) {
            return false;
        }
        if (d.getChainRegistered() == null || d.getChainRegistered() != 1) {
            return false;
        }
        if (Constants.ASSEMBLED.equals(d.getStatus())) {
            return false;
        }
        return d.getReleasedToAssembler() == null || d.getReleasedToAssembler() != 1;
    }
}
