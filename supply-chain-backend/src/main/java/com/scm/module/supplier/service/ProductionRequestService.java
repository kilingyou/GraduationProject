package com.scm.module.supplier.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.scm.module.supplier.entity.ProductionRequest;

public interface ProductionRequestService extends IService<ProductionRequest> {

    ProductionRequest createOrder(ProductionRequest request);

    IPage<ProductionRequest> listBySupplier(Long supplierId, Page<ProductionRequest> page, String status);

    IPage<ProductionRequest> listForManufacturer(Long manufacturerId, Page<ProductionRequest> page);
}
