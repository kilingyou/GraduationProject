package com.scm.module.manufacturer.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.scm.module.manufacturer.dto.DeviceRegisterRequest;
import com.scm.module.manufacturer.entity.DeviceRecord;

import java.util.List;

public interface DeviceRecordService extends IService<DeviceRecord> {

    List<String> generateEcids(String batchId, String orderId, Long manufacturerId, Integer qty, String deviceType);

    List<String> generateEcidsForBatch(String batchId, Long manufacturerId, Integer qty, String deviceType);

    List<DeviceRecord> listByBatch(String batchId);

    IPage<DeviceRecord> pageForManufacturer(Long manufacturerId, Page<DeviceRecord> page, String batchId);

    /**
     * Stub: register device records on chain by IDs.
     */
    boolean registerOnChain(List<Long> ids);

    boolean registerOnChain(DeviceRegisterRequest request, Long manufacturerId);
}
