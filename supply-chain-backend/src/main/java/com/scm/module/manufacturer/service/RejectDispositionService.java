package com.scm.module.manufacturer.service;

import com.scm.common.PageResult;
import com.scm.module.manufacturer.dto.RejectRecordVO;

/**
 * 不合格记录上链后的退货 / 销毁处置闭环。
 */
public interface RejectDispositionService {

    PageResult<RejectRecordVO> pageForSupplier(Long supplierId, int pageNum, int pageSize);

    PageResult<RejectRecordVO> pageForManufacturer(Long manufacturerId, int pageNum, int pageSize);

    void confirmReturnBySupplier(Long recordId, Long supplierId);

    void confirmDestroyByManufacturer(Long recordId, Long manufacturerId);
}
