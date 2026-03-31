package com.scm.module.regulator.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.text.Document;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfWriter;
import com.scm.common.PageResult;
import com.scm.common.Result;
import com.scm.module.distributor.entity.SalesRecord;
import com.scm.module.distributor.service.SalesRecordService;
import com.scm.module.enduser.service.TraceService;
import com.scm.module.manufacturer.entity.DeviceRecord;
import com.scm.module.assembler.entity.AssemblyRecord;
import com.scm.module.assembler.service.AssemblyRecordService;
import com.scm.module.manufacturer.service.DeviceRecordService;
import com.scm.module.regulator.entity.RecallNotice;
import com.scm.module.regulator.scheduler.AutoRecallScheduler;
import com.scm.module.regulator.service.RecallNoticeService;
import com.scm.module.regulator.service.SupplyAnomalyService;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/regulator/recall")
@RequiredArgsConstructor
public class RecallController {

    private final RecallNoticeService recallNoticeService;
    private final AssemblyRecordService assemblyRecordService;
    private final DeviceRecordService deviceRecordService;
    private final ObjectMapper objectMapper;
    private final TraceService traceService;
    private final AutoRecallScheduler autoRecallScheduler;
    private final SalesRecordService salesRecordService;
    private final SupplyAnomalyService supplyAnomalyService;

    @PostMapping
    public Result<RecallNotice> create(@RequestBody RecallNotice notice) {
        LoginUser loginUser = getCurrentUser();
        notice.setIssuerId(loginUser.getUserId());
        RecallNotice created = recallNoticeService.createNotice(notice);
        return Result.ok(created);
    }

    @GetMapping("/list")
    public Result<PageResult<RecallNotice>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<RecallNotice> page = new Page<>(pageNum, pageSize);
        IPage<RecallNotice> result = recallNoticeService.listNotices(page);

        PageResult<RecallNotice> pageResult = new PageResult<RecallNotice>()
                .setRecords(result.getRecords())
                .setTotal(result.getTotal())
                .setCurrent(result.getCurrent())
                .setSize(result.getSize());
        return Result.ok(pageResult);
    }

    @GetMapping("/analyze/{sn}")
    public Result<Map<String, Object>> analyze(@PathVariable String sn) {
        Map<String, Object> analysis = new HashMap<>();
        analysis.put("sn", sn);

        AssemblyRecord record = assemblyRecordService.listBySn(sn);
        if (record != null) {
            analysis.put("assemblyRecord", record);
            analysis.put("ecidList", record.getEcidList());

            // PDF 逻辑：从整机 SN 逆向溯源到部件 ECID，再定位“缺陷可能发生的部件批次”。
            // 当前版本无法知道具体哪颗部件是缺陷源，这里采用“第一个 ECID 的 device batchId”作为演示用故障批次。
            List<String> ecids = parseEcids(record.getEcidList());
            String faultEcid = ecids.isEmpty() ? null : ecids.get(0);
            String faultBatchId = null;
            if (faultEcid != null) {
                DeviceRecord device = deviceRecordService.getOne(new LambdaQueryWrapper<DeviceRecord>()
                        .eq(DeviceRecord::getEcid, faultEcid));
                faultBatchId = device != null ? device.getBatchId() : null;
            }

            analysis.put("faultEcid", faultEcid);
            analysis.put("faultBatchId", faultBatchId);
            // 保留给前端原字段使用：batchNo 将被视为“故障批次”（对应 RecallNotice.faultBatchId）
            analysis.put("batchNo", faultBatchId);
            analysis.put("assemblyBatchNo", record.getAssemblyBatchNo());
            analysis.put("status", record.getStatus());
        } else {
            analysis.put("message", "No assembly record found for SN: " + sn);
        }

        return Result.ok(analysis);
    }

    /**
     * 审计证据包导出（JSON 版）：覆盖 SN 全链路摘要，便于监管留档。
     */
    @GetMapping("/evidence/{sn}")
    public Result<Map<String, Object>> exportEvidence(@PathVariable String sn) {
        Map<String, Object> trace = traceService.traceProduct(sn);
        Map<String, Object> pack = new HashMap<>();
        pack.put("sn", sn);
        pack.put("generatedAt", java.time.LocalDateTime.now());
        pack.put("trace", trace);
        pack.put("note", "JSON evidence package; can be further rendered to PDF by offline tools.");
        return Result.ok(pack);
    }

    /**
     * 审计证据包导出（PDF 版）。
     */
    @GetMapping("/evidence/{sn}/pdf")
    public ResponseEntity<byte[]> exportEvidencePdf(@PathVariable String sn) {
        Map<String, Object> trace = traceService.traceProduct(sn);
        byte[] pdf = renderEvidencePdf(sn, trace);
        String fileName = "evidence-" + sn + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + new String(fileName.getBytes(StandardCharsets.UTF_8),
                                StandardCharsets.ISO_8859_1) + "\"")
                .body(pdf);
    }

    /**
     * 串货/异常监控（按 SN）：输出物流链条、绑定与销售一致性风险。
     */
    @GetMapping("/anomaly/{sn}")
    public Result<Map<String, Object>> analyzeAnomaly(@PathVariable String sn) {
        return Result.ok(supplyAnomalyService.analyzeSn(sn));
    }

    /**
     * 基于近期销售记录的 SN 批量扫描串货/异常风险（用于监管看板）。
     */
    @GetMapping("/anomalies/recent")
    public Result<List<Map<String, Object>>> recentAnomalies(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "true") boolean onlyRisk) {
        int cap = Math.max(1, Math.min(limit, 200));
        int fetch = Math.min(cap * 8, 800);
        List<SalesRecord> sales = salesRecordService.list(new LambdaQueryWrapper<SalesRecord>()
                .orderByDesc(SalesRecord::getCreateTime)
                .last("LIMIT " + fetch));

        LinkedHashSet<String> sns = new LinkedHashSet<>();
        for (SalesRecord s : sales) {
            if (s.getSn() != null && !s.getSn().trim().isEmpty()) {
                sns.add(s.getSn().trim());
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (String sn : sns) {
            Map<String, Object> row = supplyAnomalyService.analyzeSn(sn);
            if (onlyRisk && "LOW".equals(row.get("riskLevel"))) {
                continue;
            }
            rows.add(row);
            if (rows.size() >= cap) {
                break;
            }
        }
        return Result.ok(rows);
    }

    @GetMapping("/scheduler/status")
    public Result<Map<String, Object>> schedulerStatus() {
        return Result.ok(autoRecallScheduler.status());
    }

    @PostMapping("/scheduler/run")
    public Result<Void> runSchedulerNow() {
        autoRecallScheduler.runNow();
        return Result.ok();
    }

    private List<String> parseEcids(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }
        try {
            List<String> list = objectMapper.readValue(raw, java.util.List.class);
            // objectMapper.readValue(..., List.class) returns List<LinkedHashMap> sometimes;
            // but our JSON is always [\"ecid1\",\"ecid2\"]. keep it defensive.
            if (list == null) return java.util.Collections.emptyList();
            List<String> out = new java.util.ArrayList<>();
            for (Object o : list) {
                if (o != null) out.add(String.valueOf(o));
            }
            return out;
        } catch (Exception e) {
            return java.util.Collections.emptyList();
        }
    }

    private LoginUser getCurrentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private byte[] renderEvidencePdf(String sn, Map<String, Object> trace) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();
            document.add(new Paragraph("Supply Chain Evidence Package"));
            document.add(new Paragraph("SN: " + sn));
            document.add(new Paragraph("Generated At: " + java.time.LocalDateTime.now()));
            document.add(new Paragraph(" "));
            document.add(new Paragraph("Trace Snapshot (JSON):"));
            document.add(new Paragraph(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(trace)));
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("PDF export failed: " + e.getMessage(), e);
        }
    }
}
