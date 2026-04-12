package com.scm.module.manufacturer.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.scm.module.manufacturer.entity.ProductionBatch;

import java.util.List;

public interface ProductionBatchService extends IService<ProductionBatch> {

    /**
     * @param bomItemId 订单关联 BOM 时必填，对应 {@code bus_bom_item.id}；计划数量不得超过「订单套数×该行用量」减去同子件已建批计划之和
     */
    ProductionBatch createBatch(String orderId, Long manufacturerId, Integer qty, Long bomItemId);

    List<ProductionBatch> listByManufacturer(Long manufacturerId);

    List<ProductionBatch> listByOrderId(String orderId);

    /**
     * 本制造商在某订单下的批次（含 BOM 行摘要填充）。
     */
    List<ProductionBatch> listByOrderIdAndManufacturer(String orderId, Long manufacturerId);

    IPage<ProductionBatch> pageByManufacturer(Long manufacturerId, Page<ProductionBatch> page, String orderId);

    /**
     * 批次完工：本批次全部 ECID 已上链且质检合格；若订单下本企业所有批次均已完工，则订单标记为 COMPLETED。
     */
    void completeBatch(String batchId, Long manufacturerId);

    /**
     * 若本批次下全部 ECID 已上链且为质检通过状态，且数量满足计划，则自动标记批次完工并视情况完结订单（不抛业务异常）。
     */
    void tryAutoCompleteBatch(String batchId, Long manufacturerId);

    /**
     * 按本批次下已生成的设备条数刷新「完成数量」（不超过计划数量）；批次已 COMPLETED 时不修改。
     * 用于生成 ECID 后与列表展示一致；真正「批次完工」仍以状态与链上/质检条件为准。
     */
    void refreshCompletedQtyFromDevices(String batchId);
}
