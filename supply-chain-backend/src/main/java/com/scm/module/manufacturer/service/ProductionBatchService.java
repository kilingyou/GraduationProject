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
}
