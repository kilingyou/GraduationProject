package com.scm.module.supplier.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.scm.module.supplier.entity.DesignDocument;

public interface DesignDocumentService extends IService<DesignDocument> {

    /** Persist metadata, SHA-256, IPFS (or stub), chain anchor Tx hash. */
    DesignDocument upload(DesignDocument doc, byte[] fileBytes);

    IPage<DesignDocument> listBySupplier(Long supplierId, Page<DesignDocument> page);

    boolean verifyHash(Long docId);

    DesignDocument getOwned(Long id, Long supplierId);

    void deleteOwnedIfUnused(Long id, Long supplierId);
}
