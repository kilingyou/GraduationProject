package com.scm.module.manufacturer.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.scm.module.manufacturer.entity.ProductionBatch;

import java.util.List;

public interface ProductionBatchService extends IService<ProductionBatch> {

    ProductionBatch createBatch(String orderId, Long manufacturerId, Integer qty);

    List<ProductionBatch> listByManufacturer(Long manufacturerId);

    List<ProductionBatch> listByOrderId(String orderId);

    IPage<ProductionBatch> pageByManufacturer(Long manufacturerId, Page<ProductionBatch> page);

    /**
     * 批次完工：本批次全部 ECID 已上链且质检合格；若订单下本企业所有批次均已完工，则订单标记为 COMPLETED。
     */
    void completeBatch(String batchId, Long manufacturerId);

    /**
     * 若本批次下全部 ECID 已上链且为质检通过状态，且数量满足计划，则自动标记批次完工并视情况完结订单（不抛业务异常）。
     */
    void tryAutoCompleteBatch(String batchId, Long manufacturerId);
}
