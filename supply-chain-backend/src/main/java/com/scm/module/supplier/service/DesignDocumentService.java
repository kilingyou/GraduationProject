package com.scm.module.supplier.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.scm.module.supplier.entity.DesignDocument;

public interface DesignDocumentService extends IService<DesignDocument> {

    DesignDocument upload(DesignDocument doc);

    IPage<DesignDocument> listBySupplier(Long supplierId, Page<DesignDocument> page);

    boolean verifyHash(Long docId);
}
