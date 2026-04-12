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

    /**
     * 制造商确认部件已发运/可交由组装商领用：要求本厂、质检合格、已链上、未组装。
     *
     * @return 新置为已放行的条数（已为 1 的不重复计数）
     */
    int releasePartsToAssemblerByEcids(List<String> ecids, Long manufacturerId);

    int releasePartsToAssemblerByBatch(String batchId, Long manufacturerId);
}
