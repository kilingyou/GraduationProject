package com.scm.module.manufacturer.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.scm.module.manufacturer.entity.DeviceRecord;

import java.util.List;

public interface DeviceRecordService extends IService<DeviceRecord> {

    List<String> generateEcids(String batchId, String orderId, Long manufacturerId, Integer qty, String deviceType);

    List<DeviceRecord> listByBatch(String batchId);

    /**
     * Stub: register device records on chain by IDs.
     */
    boolean registerOnChain(List<Long> ids);
}
