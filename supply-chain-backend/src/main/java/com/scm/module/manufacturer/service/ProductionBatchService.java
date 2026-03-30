package com.scm.module.manufacturer.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.scm.module.manufacturer.entity.ProductionBatch;

import java.util.List;

public interface ProductionBatchService extends IService<ProductionBatch> {

    ProductionBatch createBatch(String orderId, Long manufacturerId, Integer qty);

    List<ProductionBatch> listByManufacturer(Long manufacturerId);
}
