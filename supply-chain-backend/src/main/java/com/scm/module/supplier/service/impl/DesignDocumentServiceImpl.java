package com.scm.module.supplier.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scm.common.exception.BusinessException;
import com.scm.integration.evidence.EvidenceStorageService;
import com.scm.module.supplier.entity.Bom;
import com.scm.module.supplier.entity.DesignDocument;
import com.scm.module.supplier.mapper.BomMapper;
import com.scm.module.supplier.mapper.DesignDocumentMapper;
import com.scm.module.supplier.service.DesignDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DesignDocumentServiceImpl extends ServiceImpl<DesignDocumentMapper, DesignDocument>
        implements DesignDocumentService {

    private final EvidenceStorageService evidenceStorageService;
    private final BomMapper bomMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DesignDocument upload(DesignDocument doc, byte[] fileBytes) {
        EvidenceStorageService.StoredEvidence ev = evidenceStorageService.store(
                fileBytes, doc.getFileName(), "DESIGN_DOC");
        doc.setFileHash(ev.getFileHash());
        doc.setIpfsCid(ev.getIpfsCid());
        doc.setChainStatus("ON_CHAIN");
        doc.setTxHash(ev.getTxHash());
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
        boolean ok = evidenceStorageService.verifyContentHash(doc.getIpfsCid(), doc.getFileHash());
        log.info("Hash verification doc id={}, match={}", docId, ok);
        return ok;
    }

    @Override
    public DesignDocument getOwned(Long id, Long supplierId) {
        DesignDocument doc = getById(id);
        if (doc == null || !supplierId.equals(doc.getSupplierId())) {
            return null;
        }
        return doc;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOwnedIfUnused(Long id, Long supplierId) {
        DesignDocument doc = getOwned(id, supplierId);
        if (doc == null) {
            throw new BusinessException("文档不存在或无权删除");
        }
        Long bomRefs = bomMapper.selectCount(
                new LambdaQueryWrapper<Bom>().eq(Bom::getDesignDocId, id));
        if (bomRefs != null && bomRefs > 0) {
            throw new BusinessException("该设计文档仍被 BOM 引用，无法删除");
        }
        removeById(id);
    }
}
