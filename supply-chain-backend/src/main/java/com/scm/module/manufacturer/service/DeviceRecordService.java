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

    /**
     * @param keyword        模糊匹配 ECID / 订单号 / 批次号 / 设备类型（OR）
     * @param status         设备状态精确匹配，如 QC_PASS、PRODUCED
     * @param chainRegistered 1 已上链；0 未上链（含 null）
     */
    IPage<DeviceRecord> pageForManufacturer(
            Long manufacturerId,
            Page<DeviceRecord> page,
            String batchId,
            String orderId,
            String keyword,
            String status,
            Integer chainRegistered);

    /**
     * Stub: register device records on chain by IDs.
     */
    boolean registerOnChain(List<Long> ids);

    boolean registerOnChain(DeviceRegisterRequest request, Long manufacturerId);
}
