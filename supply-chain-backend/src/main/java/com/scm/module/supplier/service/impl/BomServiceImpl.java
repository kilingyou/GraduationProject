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
        // 1) 新建 BOM 时，先将链上状态置为“待上链”
        //    这样即使后续上链失败，数据库里也能明确看到当前记录的处理阶段
        bom.setChainStatus("PENDING");
        // 2) 先保存 BOM 主表数据
        //    save 后通常会回填主键 bom.getId()，后面子项需要用这个 ID 建立关联
        save(bom);
        // 3) 保存 BOM 明细项（子物料）
        //    - 判空：避免空指针和无意义循环
        //    - 给每个明细设置 bomId，建立“主表-子表”关联关系
        if (items != null && !items.isEmpty()) {
            for (BomItem item : items) {
                item.setBomId(bom.getId());
                bomItemMapper.insert(item);
            }
        }
        List<BomItem> persisted = bomItemMapper.selectList(
                new LambdaQueryWrapper<BomItem>().eq(BomItem::getBomId, bom.getId()));
        try {
            // 5) 组装上链清单（manifest）数据结构
            //    使用 LinkedHashMap 保留插入顺序，序列化后的 JSON 字段顺序更稳定、可读性更好
            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("bomId", bom.getId());
            manifest.put("bomName", bom.getBomName());
            manifest.put("version", bom.getVersion());
            manifest.put("designDocId", bom.getDesignDocId());
            manifest.put("supplierId", bom.getSupplierId());
            // 6) 将 BOM 明细转换为可序列化的结构（List<Map>）
            //    每个子项只抽取需要上链/存证的关键字段
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
            // 7) 序列化清单为 JSON 字节数组，准备进行存证/上链
            byte[] bytes = objectMapper.writeValueAsBytes(manifest);
            // 8) 调用存证服务：
            //    - 传入内容 bytes
            //    - 生成可追踪的文件名 bom-{id}.json
            //    - 指定业务类型 BOM_MANIFEST
            //    返回结果通常包含：文件哈希、IPFS CID、链上交易哈希
            EvidenceStorageService.StoredEvidence ev =
                    evidenceStorageService.store(bytes, "bom-" + bom.getId() + ".json", "BOM_MANIFEST");
            // 9) 将存证返回信息回写到 BOM 记录，形成“业务数据 <-> 链上凭证”的映射
            bom.setFileHash(ev.getFileHash());
            bom.setIpfsCid(ev.getIpfsCid());
            bom.setTxHash(ev.getTxHash());
            // 10) 上链成功后，更新链上状态
            bom.setChainStatus("ON_CHAIN");
            // 11) 更新 BOM 主表（写回链上相关字段和状态）
            updateById(bom);
        } catch (Exception e) {
            log.error("BOM manifest chain step failed: bomId={}", bom.getId(), e);
            throw new BusinessException("BOM 保存成功但清单上链失败: " + e.getMessage());
        }
        // 14) 将持久化后的明细回填到返回对象，便于调用方直接拿到完整数据
        bom.setItems(persisted);
        log.info("BOM created: id={}, name={}, items={}", bom.getId(), bom.getBomName(), persisted.size());
        // 16) 返回创建完成（且已上链成功）的 BOM 对象
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
