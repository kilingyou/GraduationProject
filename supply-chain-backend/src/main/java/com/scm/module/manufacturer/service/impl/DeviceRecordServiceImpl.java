package com.scm.module.manufacturer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scm.common.Constants;
import com.scm.common.exception.BusinessException;
import com.scm.common.util.HashUtil;
import com.scm.integration.blockchain.BlockchainAnchorService;
import com.scm.module.manufacturer.dto.DeviceRegisterRequest;
import com.scm.module.manufacturer.entity.DeviceRecord;
import com.scm.module.manufacturer.entity.ProductionBatch;
import com.scm.module.manufacturer.mapper.DeviceRecordMapper;
import com.scm.module.manufacturer.service.DeviceRecordService;
import com.scm.module.manufacturer.service.ProductionBatchService;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeviceRecordServiceImpl
        extends ServiceImpl<DeviceRecordMapper, DeviceRecord>
        implements DeviceRecordService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ProductionBatchService productionBatchService;
    private final BlockchainAnchorService blockchainAnchorService;

    @Override
    public List<String> generateEcids(String batchId, String orderId, Long manufacturerId, Integer qty, String deviceType) {
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
                    .setDeviceType(deviceType)
                    .setManufactureTime(now)
                    .setStatus("PRODUCED")
                    .setChainRegistered(0);
            records.add(record);
        }
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
        return page(page, w);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean registerOnChain(List<Long> ids) {
        List<DeviceRecord> records = listByIds(ids);
        List<String> rejectedEcids = records.stream()
                .filter(r -> Constants.REJECTED.equals(r.getStatus()))
                .map(DeviceRecord::getEcid)
                .collect(Collectors.toList());
        if (!rejectedEcids.isEmpty()) {
            throw new BusinessException(
                    "质检不合格的设备不能上链注册，请先处理或取消勾选：" + String.join("、", rejectedEcids));
        }
        for (DeviceRecord record : records) {
            String payload = record.getEcid() + "|" + record.getOrderId() + "|"
                    + record.getBatchId() + "|" + record.getManufacturerId();
            String txHash = blockchainAnchorService.anchor(
                    "DEVICE_REGISTER", HashUtil.sha256Hex(payload));
            record.setChainRegistered(1);
            record.setTxHash(txHash);
            if (!Constants.QC_PASS.equals(record.getStatus())) {
                record.setStatus(Constants.QC_PASS);
            }
        }
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
}
