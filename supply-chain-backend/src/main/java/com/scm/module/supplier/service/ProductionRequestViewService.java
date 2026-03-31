package com.scm.module.supplier.service;

import com.scm.common.PageResult;
import com.scm.module.supplier.dto.ProductionOrderTrackVO;
import com.scm.module.supplier.dto.ProductionRequestVO;

public interface ProductionRequestViewService {

    PageResult<ProductionRequestVO> pageForSupplier(Long supplierId, int pageNum, int pageSize, String status);

    ProductionRequestVO detailForSupplier(Long id, Long supplierId);

    ProductionOrderTrackVO trackForSupplier(Long id, Long supplierId);
}
