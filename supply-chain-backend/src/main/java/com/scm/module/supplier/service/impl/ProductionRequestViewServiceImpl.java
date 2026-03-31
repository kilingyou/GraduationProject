package com.scm.module.supplier.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scm.common.Constants;
import com.scm.common.PageResult;
import com.scm.module.manufacturer.entity.DeviceRecord;
import com.scm.module.manufacturer.entity.ManufacturingAgreement;
import com.scm.module.manufacturer.entity.ProductionBatch;
import com.scm.module.manufacturer.entity.QualityReport;
import com.scm.module.manufacturer.service.DeviceRecordService;
import com.scm.module.manufacturer.service.ManufacturingAgreementService;
import com.scm.module.manufacturer.service.ProductionBatchService;
import com.scm.module.manufacturer.service.QualityReportService;
import com.scm.module.supplier.dto.ProductionOrderTrackVO;
import com.scm.module.supplier.dto.ProductionRequestVO;
import com.scm.module.supplier.entity.Bom;
import com.scm.module.supplier.entity.DesignDocument;
import com.scm.module.supplier.entity.ProductionRequest;
import com.scm.module.supplier.service.BomService;
import com.scm.module.supplier.service.DesignDocumentService;
import com.scm.module.supplier.service.ProductionRequestService;
import com.scm.module.supplier.service.ProductionRequestViewService;
import com.scm.module.system.entity.SysUser;
import com.scm.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductionRequestViewServiceImpl implements ProductionRequestViewService {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ProductionRequestService productionRequestService;
    private final BomService bomService;
    private final DesignDocumentService designDocumentService;
    private final SysUserMapper sysUserMapper;
    private final ManufacturingAgreementService manufacturingAgreementService;
    private final ProductionBatchService productionBatchService;
    private final DeviceRecordService deviceRecordService;
    private final QualityReportService qualityReportService;

    @Override
    public PageResult<ProductionRequestVO> pageForSupplier(Long supplierId, int pageNum, int pageSize, String status) {
        Page<ProductionRequest> page = new Page<>(pageNum, pageSize);
        IPage<ProductionRequest> raw = productionRequestService.listBySupplier(supplierId, page, status);
        List<ProductionRequestVO> vos = raw.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return new PageResult<ProductionRequestVO>()
                .setRecords(vos)
                .setTotal(raw.getTotal())
                .setCurrent(raw.getCurrent())
                .setSize(raw.getSize());
    }

    @Override
    public ProductionRequestVO detailForSupplier(Long id, Long supplierId) {
        ProductionRequest order = productionRequestService.getById(id);
        if (order == null || !supplierId.equals(order.getSupplierId())) {
            return null;
        }
        return toVO(order);
    }

    @Override
    public ProductionOrderTrackVO trackForSupplier(Long id, Long supplierId) {
        ProductionRequest order = productionRequestService.getById(id);
        if (order == null || !supplierId.equals(order.getSupplierId())) {
            return null;
        }

        ProductionOrderTrackVO track = new ProductionOrderTrackVO();
        if (Constants.CANCELLED.equals(order.getStatus())) {
            Map<String, String> cancelledTimes = new LinkedHashMap<>();
            LocalDateTime cancelledAt = order.getUpdateTime() != null ? order.getUpdateTime() : order.getCreateTime();
            cancelledTimes.put("CANCELLED", fmt(cancelledAt));
            track.setStatusTimes(cancelledTimes);
            return track;
        }
        Map<String, String> times = new LinkedHashMap<>();
        times.put("PENDING_ACCEPTANCE", fmt(order.getCreateTime()));

        ManufacturingAgreement agreement = manufacturingAgreementService.getOne(
                new LambdaQueryWrapper<ManufacturingAgreement>()
                        .eq(ManufacturingAgreement::getOrderId, order.getOrderId()));
        if (agreement != null) {
            ProductionOrderTrackVO.AgreementSummary s = new ProductionOrderTrackVO.AgreementSummary();
            SysUser m = sysUserMapper.selectById(agreement.getManufacturerId());
            s.setManufacturerName(displayUserName(m));
            s.setPromisedDelivery(agreement.getDeliveryDate());
            s.setAgreedPrice(agreement.getFinalPrice());
            s.setStatus("SIGNED");
            track.setAgreement(s);
            times.put("ACCEPTED", fmt(agreement.getCreateTime()));
        }

        List<ProductionBatch> batches = productionBatchService.listByOrderId(order.getOrderId());
        if (!batches.isEmpty()) {
            times.put("IN_PRODUCTION", fmt(batches.get(0).getCreateTime()));
            batches.stream()
                    .filter(b -> "COMPLETED".equals(b.getStatus()))
                    .max(Comparator.comparing(ProductionBatch::getUpdateTime, Comparator.nullsLast(Comparator.naturalOrder())))
                    .ifPresent(b -> times.put("COMPLETED", fmt(b.getUpdateTime())));
        }

        List<DeviceRecord> devices = deviceRecordService.list(
                new LambdaQueryWrapper<DeviceRecord>()
                        .eq(DeviceRecord::getOrderId, order.getOrderId())
                        .orderByAsc(DeviceRecord::getCreateTime));
        List<ProductionOrderTrackVO.EcidRow> ecidRows = new ArrayList<>();
        for (DeviceRecord d : devices) {
            ProductionOrderTrackVO.EcidRow row = new ProductionOrderTrackVO.EcidRow();
            row.setEcid(d.getEcid());
            row.setStatus(d.getStatus());
            row.setCreateTime(fmt(d.getCreateTime()));
            ecidRows.add(row);
        }
        track.setEcidList(ecidRows);

        List<String> ecids = devices.stream().map(DeviceRecord::getEcid).filter(Objects::nonNull).collect(Collectors.toList());
        List<String> batchIds = batches.stream().map(ProductionBatch::getBatchId).filter(Objects::nonNull).collect(Collectors.toList());

        List<QualityReport> reportList = new ArrayList<>();
        if (!ecids.isEmpty()) {
            reportList.addAll(qualityReportService.list(new LambdaQueryWrapper<QualityReport>()
                    .eq(QualityReport::getReportType, "MANUFACTURE")
                    .eq(QualityReport::getTargetType, "ECID")
                    .in(QualityReport::getTargetId, ecids)));
        }
        if (!batchIds.isEmpty()) {
            reportList.addAll(qualityReportService.list(new LambdaQueryWrapper<QualityReport>()
                    .eq(QualityReport::getReportType, "MANUFACTURE")
                    .eq(QualityReport::getTargetType, "BATCH")
                    .in(QualityReport::getTargetId, batchIds)));
        }
        Map<Long, QualityReport> byId = new LinkedHashMap<>();
        for (QualityReport r : reportList) {
            byId.putIfAbsent(r.getId(), r);
        }
        List<ProductionOrderTrackVO.ReportRow> reports = byId.values().stream()
                .sorted(Comparator.comparing(QualityReport::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(r -> {
                    ProductionOrderTrackVO.ReportRow row = new ProductionOrderTrackVO.ReportRow();
                    row.setReportName(r.getReportName());
                    row.setResult(r.getResult());
                    row.setCreateTime(fmt(r.getCreateTime()));
                    return row;
                })
                .collect(Collectors.toList());
        track.setTestReports(reports);

        track.setStatusTimes(times);
        return track;
    }

    private ProductionRequestVO toVO(ProductionRequest order) {
        ProductionRequestVO vo = new ProductionRequestVO();
        BeanUtils.copyProperties(order, vo);
        if (order.getBomId() != null) {
            Bom b = bomService.getById(order.getBomId());
            if (b != null) {
                vo.setBomName(b.getBomName());
            }
        }
        Long docId = order.getDesignDocId();
        if (docId == null && order.getBomId() != null) {
            Bom b = bomService.getById(order.getBomId());
            if (b != null) {
                docId = b.getDesignDocId();
            }
        }
        if (docId != null) {
            DesignDocument d = designDocumentService.getById(docId);
            if (d != null) {
                vo.setDesignDocName(d.getDocName());
            }
        }
        if (order.getTargetManufacturer() != null) {
            SysUser u = sysUserMapper.selectById(order.getTargetManufacturer());
            vo.setTargetManufacturerName(displayUserName(u));
        }
        return vo;
    }

    private static String displayUserName(SysUser u) {
        if (u == null) {
            return null;
        }
        if (StringUtils.hasText(u.getEnterpriseName())) {
            return u.getEnterpriseName().trim();
        }
        return u.getUsername();
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? "" : TS_FMT.format(t);
    }
}
