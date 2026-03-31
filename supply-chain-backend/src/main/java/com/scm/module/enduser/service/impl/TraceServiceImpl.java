package com.scm.module.enduser.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scm.module.assembler.entity.AssemblyRecord;
import com.scm.module.assembler.service.AssemblyRecordService;
import com.scm.module.distributor.entity.SalesRecord;
import com.scm.module.distributor.entity.TransferEvent;
import com.scm.module.distributor.service.SalesRecordService;
import com.scm.module.distributor.service.TransferEventService;
import com.scm.module.enduser.service.TraceService;
import com.scm.module.manufacturer.entity.DeviceRecord;
import com.scm.module.manufacturer.entity.QualityReport;
import com.scm.module.manufacturer.service.DeviceRecordService;
import com.scm.module.manufacturer.service.QualityReportService;
import com.scm.module.supplier.entity.Bom;
import com.scm.module.supplier.entity.DesignDocument;
import com.scm.module.supplier.entity.ProductionRequest;
import com.scm.module.supplier.service.BomService;
import com.scm.module.supplier.service.DesignDocumentService;
import com.scm.module.supplier.service.ProductionRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TraceServiceImpl implements TraceService {

    private final AssemblyRecordService assemblyRecordService;
    private final TransferEventService transferEventService;
    private final SalesRecordService salesRecordService;
    private final DeviceRecordService deviceRecordService;
    private final QualityReportService qualityReportService;
    private final ProductionRequestService productionRequestService;
    private final BomService bomService;
    private final DesignDocumentService designDocumentService;
    private final ObjectMapper objectMapper;

    @Override
    public Map<String, Object> traceProduct(String sn) {
        String qsn = sn != null ? sn.trim() : "";
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("sn", qsn);

        AssemblyRecord record = assemblyRecordService.listBySn(qsn);
        List<String> warnings = new ArrayList<>();
        if (record != null) {
            trace.put("assemblyRecord", record);
            trace.put("ecidList", record.getEcidList());
            trace.put("assemblyTime", record.getAssemblyTime());
            trace.put("firmwareVersion", record.getFirmwareVersion());
            trace.put("status", record.getStatus());
            if (record.getChainRegistered() == null || record.getChainRegistered() != 1) {
                warnings.add("该整机装配映射尚未完成链上登记，溯源信息仅供参考");
            }
            if ("DECOMMISSIONED".equals(record.getStatus())) {
                warnings.add("该产品已登记报废，请勿继续流通或使用");
            }
        } else {
            warnings.add("未查询到该 SN 的装配记录，可能为伪造标识或尚未录入系统");
        }
        trace.put("warnings", warnings);

        List<TransferEvent> transfers = transferEventService.listBySn(qsn);
        trace.put("transferEvents", transfers);

        SalesRecord sale = salesRecordService.getLatestBySn(qsn);
        trace.put("salesRecord", sale);

        List<Map<String, Object>> deviceTraces = new ArrayList<>();
        if (record != null) {
            for (String ecid : parseEcids(record.getEcidList())) {
                deviceTraces.add(buildDeviceTrace(ecid));
            }
        }
        trace.put("deviceTraces", deviceTraces);

        return trace;
    }

    private List<String> parseEcids(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<String> list = objectMapper.readValue(raw, new TypeReference<List<String>>() {});
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private Map<String, Object> buildDeviceTrace(String ecid) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ecid", ecid);
        DeviceRecord device = deviceRecordService.getOne(new LambdaQueryWrapper<DeviceRecord>()
                .eq(DeviceRecord::getEcid, ecid));
        m.put("deviceRecord", device);
        if (device == null) {
            return m;
        }
        List<QualityReport> reports = qualityReportService.list(new LambdaQueryWrapper<QualityReport>()
                .eq(QualityReport::getTargetType, "ECID")
                .eq(QualityReport::getTargetId, ecid)
                .orderByDesc(QualityReport::getCreateTime));
        m.put("qualityReports", reports);

        if (device.getOrderId() != null) {
            ProductionRequest order = productionRequestService.getOne(new LambdaQueryWrapper<ProductionRequest>()
                    .eq(ProductionRequest::getOrderId, device.getOrderId()));
            m.put("productionChain", summarizeProductionChain(order));
        }
        return m;
    }

    private Map<String, Object> summarizeProductionChain(ProductionRequest order) {
        if (order == null) {
            return null;
        }
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("orderId", order.getOrderId());
        s.put("status", order.getStatus());
        s.put("txHash", order.getTxHash());
        s.put("quantity", order.getQuantity());
        s.put("designDocHashSnapshot", order.getDesignDocHash());

        if (order.getBomId() != null) {
            Bom bom = bomService.getById(order.getBomId());
            if (bom != null) {
                Map<String, Object> b = new LinkedHashMap<>();
                b.put("id", bom.getId());
                b.put("bomName", bom.getBomName());
                b.put("version", bom.getVersion());
                b.put("fileHash", bom.getFileHash());
                b.put("ipfsCid", bom.getIpfsCid());
                b.put("chainStatus", bom.getChainStatus());
                s.put("bom", b);
            }
        }
        if (order.getDesignDocId() != null) {
            DesignDocument doc = designDocumentService.getById(order.getDesignDocId());
            if (doc != null) {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("id", doc.getId());
                d.put("docName", doc.getDocName());
                d.put("version", doc.getVersion());
                d.put("fileHash", doc.getFileHash());
                d.put("ipfsCid", doc.getIpfsCid());
                d.put("chainStatus", doc.getChainStatus());
                s.put("designDocument", d);
            }
        }
        return s;
    }
}
