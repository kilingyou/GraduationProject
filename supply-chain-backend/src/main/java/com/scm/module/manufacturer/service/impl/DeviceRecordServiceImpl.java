package com.scm.module.manufacturer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scm.module.manufacturer.entity.DeviceRecord;
import com.scm.module.manufacturer.mapper.DeviceRecordMapper;
import com.scm.module.manufacturer.service.DeviceRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeviceRecordServiceImpl
        extends ServiceImpl<DeviceRecordMapper, DeviceRecord>
        implements DeviceRecordService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

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
    public List<DeviceRecord> listByBatch(String batchId) {
        return list(new LambdaQueryWrapper<DeviceRecord>()
                .eq(DeviceRecord::getBatchId, batchId)
                .orderByAsc(DeviceRecord::getEcid));
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
}
