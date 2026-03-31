package com.scm.module.manufacturer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scm.common.PageResult;
import com.scm.common.Constants;
import com.scm.module.manufacturer.dto.ManufacturerOrderVO;
import com.scm.module.manufacturer.service.ManufacturerOrderViewService;
import com.scm.module.supplier.entity.Bom;
import com.scm.module.supplier.entity.DesignDocument;
import com.scm.module.supplier.entity.ProductionRequest;
import com.scm.module.supplier.service.BomService;
import com.scm.module.supplier.service.DesignDocumentService;
import com.scm.module.supplier.service.ProductionRequestService;
import com.scm.module.system.entity.SysUser;
import com.scm.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ManufacturerOrderViewServiceImpl implements ManufacturerOrderViewService {

    private final ProductionRequestService productionRequestService;
    private final BomService bomService;
    private final DesignDocumentService designDocumentService;
    private final SysUserMapper sysUserMapper;

    @Value("${scm.ipfs.gateway:}")
    private String ipfsGateway;

    @Override
    public PageResult<ManufacturerOrderVO> pageOrderPool(Long manufacturerId, int page, int pageSize, String keyword) {
        Page<ProductionRequest> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<ProductionRequest> w = new LambdaQueryWrapper<ProductionRequest>()
                .eq(ProductionRequest::getStatus, Constants.PENDING_ACCEPTANCE)
                .and(q -> q.isNull(ProductionRequest::getTargetManufacturer)
                        .or()
                        .eq(ProductionRequest::getTargetManufacturer, manufacturerId));
        applyKeyword(w, keyword);
        w.orderByDesc(ProductionRequest::getCreateTime);
        IPage<ProductionRequest> raw = productionRequestService.page(p, w);
        return toPageResult(raw);
    }

    @Override
    public PageResult<ManufacturerOrderVO> pageMyOrders(Long manufacturerId, int page, int pageSize, String keyword,
                                                        String status) {
        Page<ProductionRequest> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<ProductionRequest> w = new LambdaQueryWrapper<ProductionRequest>()
                .apply("EXISTS (SELECT 1 FROM bus_manufacturing_agreement ma WHERE ma.order_id = bus_production_request.order_id AND ma.manufacturer_id = {0})",
                        manufacturerId);
        if (StringUtils.hasText(status)) {
            w.eq(ProductionRequest::getStatus, status);
        } else {
            w.notIn(ProductionRequest::getStatus,
                    Constants.PENDING_ACCEPTANCE,
                    Constants.CANCELLED);
        }
        applyKeyword(w, keyword);
        w.orderByDesc(ProductionRequest::getCreateTime);
        IPage<ProductionRequest> raw = productionRequestService.page(p, w);
        return toPageResult(raw);
    }

    private void applyKeyword(LambdaQueryWrapper<ProductionRequest> w, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        String kw = keyword.trim();
        List<Long> bomIds = bomService.list(new LambdaQueryWrapper<Bom>().like(Bom::getBomName, kw))
                .stream()
                .map(Bom::getId)
                .collect(Collectors.toList());
        w.and(q -> {
            q.like(ProductionRequest::getOrderId, kw);
            if (!bomIds.isEmpty()) {
                q.or().in(ProductionRequest::getBomId, bomIds);
            }
        });
    }

    private PageResult<ManufacturerOrderVO> toPageResult(IPage<ProductionRequest> raw) {
        List<ProductionRequest> records = raw.getRecords();
        if (records.isEmpty()) {
            return new PageResult<ManufacturerOrderVO>()
                    .setRecords(Collections.emptyList())
                    .setTotal(raw.getTotal())
                    .setCurrent(raw.getCurrent())
                    .setSize(raw.getSize());
        }
        List<Long> bomIds = records.stream()
                .map(ProductionRequest::getBomId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Bom> bomById = bomIds.isEmpty()
                ? Collections.emptyMap()
                : bomService.listByIds(bomIds).stream().collect(Collectors.toMap(Bom::getId, Function.identity()));

        List<Long> supplierIds = records.stream()
                .map(ProductionRequest::getSupplierId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, SysUser> userById = supplierIds.isEmpty()
                ? Collections.emptyMap()
                : sysUserMapper.selectBatchIds(supplierIds).stream().collect(Collectors.toMap(SysUser::getId, Function.identity()));

        List<Long> docIds = records.stream()
                .map(ProductionRequest::getDesignDocId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        for (ProductionRequest pr : records) {
            if (pr.getDesignDocId() == null && pr.getBomId() != null) {
                Bom b = bomById.get(pr.getBomId());
                if (b != null && b.getDesignDocId() != null) {
                    docIds.add(b.getDesignDocId());
                }
            }
        }
        docIds = docIds.stream().distinct().collect(Collectors.toList());
        Map<Long, DesignDocument> docById = docIds.isEmpty()
                ? Collections.emptyMap()
                : designDocumentService.listByIds(docIds).stream()
                .collect(Collectors.toMap(DesignDocument::getId, Function.identity()));

        List<ManufacturerOrderVO> vos = records.stream()
                .map(r -> toVo(r, bomById, docById, userById))
                .collect(Collectors.toList());
        return new PageResult<ManufacturerOrderVO>()
                .setRecords(vos)
                .setTotal(raw.getTotal())
                .setCurrent(raw.getCurrent())
                .setSize(raw.getSize());
    }

    private ManufacturerOrderVO toVo(ProductionRequest r, Map<Long, Bom> bomById, Map<Long, DesignDocument> docById,
                                     Map<Long, SysUser> userById) {
        ManufacturerOrderVO vo = new ManufacturerOrderVO();
        BeanUtils.copyProperties(r, vo);
        if (r.getBomId() != null) {
            Bom b = bomById.get(r.getBomId());
            if (b != null) {
                vo.setBomName(b.getBomName());
            }
        }
        Long docId = r.getDesignDocId();
        if (docId == null && r.getBomId() != null) {
            Bom b = bomById.get(r.getBomId());
            if (b != null) {
                docId = b.getDesignDocId();
            }
        }
        if (docId != null) {
            DesignDocument d = docById.get(docId);
            if (d != null) {
                vo.setDesignDocName(d.getDocName());
                vo.setDesignDocFileHash(d.getFileHash());
                vo.setDesignDocIpfsCid(d.getIpfsCid());
                if (StringUtils.hasText(d.getIpfsCid()) && StringUtils.hasText(ipfsGateway)) {
                    String base = ipfsGateway.endsWith("/") ? ipfsGateway : ipfsGateway + "/";
                    vo.setDesignDocDownloadUrl(base + d.getIpfsCid());
                }
            }
        }
        if (r.getSupplierId() != null) {
            SysUser u = userById.get(r.getSupplierId());
            if (u != null) {
                vo.setSupplierEnterpriseName(StringUtils.hasText(u.getEnterpriseName())
                        ? u.getEnterpriseName() : u.getUsername());
            }
        }
        return vo;
    }
}
