package com.scm.module.manufacturer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scm.common.exception.BusinessException;
import com.scm.module.manufacturer.dto.DeviceRegisterRequest;
import com.scm.module.manufacturer.entity.DeviceRecord;
import com.scm.module.manufacturer.entity.ProductionBatch;
import com.scm.module.manufacturer.mapper.DeviceRecordMapper;
import com.scm.module.manufacturer.service.DeviceRecordService;
import com.scm.module.manufacturer.service.ProductionBatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeviceRecordServiceImpl
        extends ServiceImpl<DeviceRecordMapper, DeviceRecord>
        implements DeviceRecordService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ProductionBatchService productionBatchService;

    @Override
    public List<String> generateEcids(String batchId, String orderId, Long manufacturerId, Integer qty, String deviceType) {
        String dateStr = LocalDate.now().format(DATE_FMT);
        String mfCode = "M" + String.format("%04d", manufacturerId % 10000);

        long existingCount = count(new LambdaQueryWrapper<DeviceRecord>()
                .eq(DeviceRecord::getBatchId, batchId));
        int startSeq = (int) existingCount + 1;

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
        return ecids;
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
    public boolean registerOnChain(List<Long> ids) {
        // TODO: integrate with blockchain SDK to register devices on chain
        List<DeviceRecord> records = listByIds(ids);
        for (DeviceRecord record : records) {
            record.setChainRegistered(1);
            record.setTxHash("0x_stub_" + record.getEcid());
        }
        return updateBatchById(records);
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
