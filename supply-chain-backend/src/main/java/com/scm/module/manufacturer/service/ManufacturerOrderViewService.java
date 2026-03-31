package com.scm.module.manufacturer.service;

import com.scm.common.PageResult;
import com.scm.module.manufacturer.dto.ManufacturerOrderVO;

public interface ManufacturerOrderViewService {

    PageResult<ManufacturerOrderVO> pageOrderPool(Long manufacturerId, int page, int pageSize, String keyword);

    PageResult<ManufacturerOrderVO> pageMyOrders(Long manufacturerId, int page, int pageSize, String keyword,
                                                   String status);
}
