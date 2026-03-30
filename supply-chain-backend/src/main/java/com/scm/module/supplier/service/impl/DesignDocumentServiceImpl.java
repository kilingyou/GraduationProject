package com.scm.module.supplier.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scm.module.supplier.entity.DesignDocument;
import com.scm.module.supplier.mapper.DesignDocumentMapper;
import com.scm.module.supplier.service.DesignDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DesignDocumentServiceImpl extends ServiceImpl<DesignDocumentMapper, DesignDocument>
        implements DesignDocumentService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DesignDocument upload(DesignDocument doc) {
        // Stub: compute file hash (SHA-256 in production)
        if (doc.getFileHash() == null) {
            doc.setFileHash(UUID.randomUUID().toString().replace("-", ""));
        }

        // Stub: upload to IPFS and get CID
        if (doc.getIpfsCid() == null) {
            doc.setIpfsCid("Qm" + UUID.randomUUID().toString().replace("-", "").substring(0, 44));
        }

        doc.setChainStatus("PENDING");
        save(doc);

        log.info("Design document uploaded: id={}, name={}", doc.getId(), doc.getDocName());
        return doc;
    }

    @Override
    public IPage<DesignDocument> listBySupplier(Long supplierId, Page<DesignDocument> page) {
        LambdaQueryWrapper<DesignDocument> wrapper = new LambdaQueryWrapper<DesignDocument>()
                .eq(DesignDocument::getSupplierId, supplierId)
                .orderByDesc(DesignDocument::getCreateTime);
        return page(page, wrapper);
    }

    @Override
    public boolean verifyHash(Long docId) {
        DesignDocument doc = getById(docId);
        if (doc == null) {
            return false;
        }
        // Stub: in production, re-compute hash from IPFS content and compare
        log.info("Hash verification stub for doc id={}, hash={}", docId, doc.getFileHash());
        return true;
    }
}
