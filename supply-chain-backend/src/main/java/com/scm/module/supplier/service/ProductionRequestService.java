package com.scm.module.supplier.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.scm.module.supplier.entity.ProductionRequest;

import java.util.List;

public interface ProductionRequestService extends IService<ProductionRequest> {

    ProductionRequest createOrder(ProductionRequest request);

    IPage<ProductionRequest> listBySupplier(Long supplierId, Page<ProductionRequest> page, String status);

    IPage<ProductionRequest> listForManufacturer(Long manufacturerId, Page<ProductionRequest> page);

    void cancelOrderBySupplier(Long orderDbId, Long supplierId);

    /**
     * 指定可领用本单部件的组装商；{@code null} 表示不限制（任意组装商可领用已放行部件）。
     */
    void designateAssemblyAssembler(Long orderDbId, Long supplierId, Long assemblyAssemblerUserId);

    /**
     * 组装商可建批次的生产订单：未撤销，且未指定组装商或指定为当前用户。
     */
    List<ProductionRequest> listAssemblyEligibleOrders(Long assemblerUserId);
}
