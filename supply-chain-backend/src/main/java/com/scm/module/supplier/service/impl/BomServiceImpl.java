package com.scm.module.supplier.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scm.common.exception.BusinessException;
import com.scm.integration.evidence.EvidenceStorageService;
import com.scm.module.supplier.dto.BomVO;
import com.scm.module.supplier.entity.Bom;
import com.scm.module.supplier.entity.BomItem;
import com.scm.module.supplier.entity.DesignDocument;
import com.scm.module.supplier.entity.ProductionRequest;
import com.scm.module.supplier.mapper.BomItemMapper;
import com.scm.module.supplier.mapper.BomMapper;
import com.scm.module.supplier.mapper.ProductionRequestMapper;
import com.scm.module.supplier.service.BomService;
import com.scm.module.supplier.service.DesignDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BomServiceImpl extends ServiceImpl<BomMapper, Bom> implements BomService {

    private final BomItemMapper bomItemMapper;
    private final ObjectMapper objectMapper;
    private final EvidenceStorageService evidenceStorageService;
    private final DesignDocumentService designDocumentService;
    private final ProductionRequestMapper productionRequestMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Bom createBom(Bom bom, List<BomItem> items) {
        bom.setChainStatus("PENDING");
        //向数据库中插入物料清单数据
        save(bom);

        //插入详细物料数据
        if (items != null && !items.isEmpty()) {
            for (BomItem item : items) {
                item.setBomId(bom.getId());
                bomItemMapper.insert(item);
            }
        }

        List<BomItem> persisted = bomItemMapper.selectList(
                new LambdaQueryWrapper<BomItem>().eq(BomItem::getBomId, bom.getId()));
        try {
            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("bomId", bom.getId());
            manifest.put("bomName", bom.getBomName());
            manifest.put("version", bom.getVersion());
            manifest.put("designDocId", bom.getDesignDocId());
            manifest.put("supplierId", bom.getSupplierId());
            List<Map<String, Object>> itemMaps = new ArrayList<>();
            for (BomItem i : persisted) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("partName", i.getPartName());
                m.put("partNumber", i.getPartNumber());
                m.put("specification", i.getSpecification());
                m.put("quantity", i.getQuantity());
                m.put("unit", i.getUnit());
                m.put("remark", i.getRemark());
                itemMaps.add(m);
            }
            manifest.put("items", itemMaps);
            byte[] bytes = objectMapper.writeValueAsBytes(manifest);
            EvidenceStorageService.StoredEvidence ev =
                    evidenceStorageService.store(bytes, "bom-" + bom.getId() + ".json", "BOM_MANIFEST");
            bom.setFileHash(ev.getFileHash());
            bom.setIpfsCid(ev.getIpfsCid());
            bom.setTxHash(ev.getTxHash());
            bom.setChainStatus("ON_CHAIN");
            updateById(bom);
        } catch (Exception e) {
            log.error("BOM manifest chain step failed: bomId={}", bom.getId(), e);
            throw new BusinessException("BOM 保存成功但清单上链失败: " + e.getMessage());
        }

        bom.setItems(persisted);
        log.info("BOM created: id={}, name={}, items={}", bom.getId(), bom.getBomName(), persisted.size());
        return bom;
    }

    @Override
    public IPage<Bom> listBySupplier(Long supplierId, Page<Bom> page) {
        LambdaQueryWrapper<Bom> wrapper = new LambdaQueryWrapper<Bom>()
                .eq(Bom::getSupplierId, supplierId)
                .orderByDesc(Bom::getCreateTime);
        return page(page, wrapper);
    }

    @Override
    public Bom getBomWithItems(Long bomId) {
        Bom bom = getById(bomId);
        if (bom == null) {
            return null;
        }

        List<BomItem> items = bomItemMapper.selectList(
                new LambdaQueryWrapper<BomItem>().eq(BomItem::getBomId, bomId));
        bom.setItems(items);
        return bom;
    }

    @Override
    public IPage<BomVO> pageVoBySupplier(Long supplierId, Page<Bom> page) {
        IPage<Bom> raw = listBySupplier(supplierId, page);
        List<Bom> records = raw.getRecords();
        if (records.isEmpty()) {
            Page<BomVO> empty = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
            empty.setRecords(Collections.emptyList());
            return empty;
        }
        List<Long> docIds = records.stream()
                .map(Bom::getDesignDocId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, DesignDocument> docById = docIds.isEmpty()
                ? Collections.emptyMap()
                : designDocumentService.listByIds(docIds).stream()
                        .collect(Collectors.toMap(DesignDocument::getId, Function.identity()));
        List<BomVO> vos = records.stream().map(b -> toVo(b, docById)).collect(Collectors.toList());
        Page<BomVO> voPage = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        voPage.setRecords(vos);
        return voPage;
    }

    @Override
    public BomVO getBomVoWithItems(Long bomId, Long supplierId) {
        Bom bom = getBomWithItems(bomId);
        if (bom == null || !supplierId.equals(bom.getSupplierId())) {
            return null;
        }
        Map<Long, DesignDocument> docById = Collections.emptyMap();
        if (bom.getDesignDocId() != null) {
            DesignDocument d = designDocumentService.getById(bom.getDesignDocId());
            if (d != null) {
                docById = Collections.singletonMap(d.getId(), d);
            }
        }
        BomVO vo = toVo(bom, docById);
        vo.setItems(bom.getItems());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeBomForSupplier(Long bomId, Long supplierId) {
        Bom bom = getById(bomId);
        if (bom == null) {
            throw new BusinessException("BOM 不存在");
        }
        if (!supplierId.equals(bom.getSupplierId())) {
            throw new BusinessException("无权删除该 BOM");
        }
        Long refs = productionRequestMapper.selectCount(
                new LambdaQueryWrapper<ProductionRequest>().eq(ProductionRequest::getBomId, bomId));
        if (refs != null && refs > 0) {
            throw new BusinessException("该 BOM 已被生产订单引用，无法删除");
        }
        bomItemMapper.delete(new LambdaQueryWrapper<BomItem>().eq(BomItem::getBomId, bomId));
        removeById(bomId);
    }

    private static BomVO toVo(Bom bom, Map<Long, DesignDocument> docById) {
        BomVO vo = new BomVO();
        BeanUtils.copyProperties(bom, vo);
        vo.setItems(null);
        if (bom.getDesignDocId() != null) {
            DesignDocument d = docById.get(bom.getDesignDocId());
            if (d != null) {
                vo.setDesignDocName(d.getDocName());
            }
        }
        return vo;
    }
}
