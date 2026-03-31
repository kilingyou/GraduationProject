package com.scm.module.regulator.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
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

    @SuppressWarnings("unchecked")
    private byte[] renderEvidencePdf(String sn, Map<String, Object> trace) {
        try {
            BaseFont bfChinese = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
            Font titleFont = new Font(bfChinese, 18, Font.BOLD);
            Font h2Font = new Font(bfChinese, 14, Font.BOLD, new BaseColor(64, 158, 255));
            Font bodyFont = new Font(bfChinese, 10, Font.NORMAL);
            Font boldFont = new Font(bfChinese, 10, Font.BOLD);
            Font smallFont = new Font(bfChinese, 8, Font.NORMAL, BaseColor.GRAY);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 40, 40, 50, 40);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Paragraph title = new Paragraph("供应链溯源证据包", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);
            doc.add(new Paragraph(" "));

            PdfPTable infoTable = newTable(2, new float[]{30, 70});
            addKvRow(infoTable, "产品SN", sn, boldFont, bodyFont);
            addKvRow(infoTable, "生成时间", java.time.LocalDateTime.now().toString(), boldFont, bodyFont);
            addKvRow(infoTable, "装配状态", str(trace.get("status")), boldFont, bodyFont);
            addKvRow(infoTable, "固件版本", str(trace.get("firmwareVersion")), boldFont, bodyFont);
            doc.add(infoTable);
            doc.add(new Paragraph(" "));

            List<?> warnings = trace.get("warnings") instanceof List ? (List<?>) trace.get("warnings") : null;
            if (warnings != null && !warnings.isEmpty()) {
                doc.add(new Paragraph("风险提示", h2Font));
                for (Object w : warnings) {
                    Paragraph pw = new Paragraph("• " + w, bodyFont);
                    pw.setIndentationLeft(20);
                    doc.add(pw);
                }
                doc.add(new Paragraph(" "));
            }

            List<?> deviceTraces = trace.get("deviceTraces") instanceof List ? (List<?>) trace.get("deviceTraces") : null;
            if (deviceTraces != null && !deviceTraces.isEmpty()) {
                doc.add(new Paragraph("部件溯源", h2Font));
                PdfPTable devTable = newTable(5, new float[]{22, 14, 14, 14, 36});
                addHeaderRow(devTable, new String[]{"ECID", "设备类型", "批次", "状态", "上链TxHash"}, boldFont);
                for (Object dt : deviceTraces) {
                    if (!(dt instanceof Map)) continue;
                    Map<String, Object> dtm = (Map<String, Object>) dt;
                    Map<String, Object> dr = dtm.get("deviceRecord") instanceof Map ? (Map<String, Object>) dtm.get("deviceRecord") : null;
                    addRow(devTable, new String[]{
                            str(dtm.get("ecid")),
                            dr != null ? str(dr.get("deviceType")) : "-",
                            dr != null ? str(dr.get("batchId")) : "-",
                            dr != null ? str(dr.get("status")) : "-",
                            dr != null ? str(dr.get("txHash")) : "-"
                    }, bodyFont);
                }
                doc.add(devTable);
                doc.add(new Paragraph(" "));
            }

            List<?> transfers = trace.get("transferEvents") instanceof List ? (List<?>) trace.get("transferEvents") : null;
            if (transfers != null && !transfers.isEmpty()) {
                doc.add(new Paragraph("物流流转记录", h2Font));
                PdfPTable tfTable = newTable(5, new float[]{18, 18, 18, 18, 28});
                addHeaderRow(tfTable, new String[]{"物流单号", "物流公司", "发货时间", "状态", "上链TxHash"}, boldFont);
                for (Object t : transfers) {
                    if (!(t instanceof Map)) {
                        try {
                            Map<String, Object> tm = objectMapper.convertValue(t, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                            addRow(tfTable, new String[]{
                                    str(tm.get("trackingNumber")), str(tm.get("logisticsCompany")),
                                    str(tm.get("shipTime")), str(tm.get("status")), str(tm.get("txHash"))
                            }, bodyFont);
                        } catch (Exception ignore) {}
                        continue;
                    }
                    Map<String, Object> tm = (Map<String, Object>) t;
                    addRow(tfTable, new String[]{
                            str(tm.get("trackingNumber")), str(tm.get("logisticsCompany")),
                            str(tm.get("shipTime")), str(tm.get("status")), str(tm.get("txHash"))
                    }, bodyFont);
                }
                doc.add(tfTable);
                doc.add(new Paragraph(" "));
            }

            Object sale = trace.get("salesRecord");
            if (sale != null) {
                doc.add(new Paragraph("销售信息", h2Font));
                Map<String, Object> sm;
                if (sale instanceof Map) {
                    sm = (Map<String, Object>) sale;
                } else {
                    sm = objectMapper.convertValue(sale, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                }
                PdfPTable saleTable = newTable(2, new float[]{30, 70});
                addKvRow(saleTable, "销售时间", str(sm.get("saleTime")), boldFont, bodyFont);
                addKvRow(saleTable, "上链TxHash", str(sm.get("txHash")), boldFont, bodyFont);
                doc.add(saleTable);
                doc.add(new Paragraph(" "));
            }

            Paragraph footer = new Paragraph("本证据包由供应链管理系统自动生成，数据来源于区块链及IPFS存储。", smallFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            doc.add(footer);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("PDF export failed: " + e.getMessage(), e);
        }
    }

    private PdfPTable newTable(int cols, float[] widths) throws DocumentException {
        PdfPTable table = new PdfPTable(cols);
        table.setWidthPercentage(100);
        table.setWidths(widths);
        table.setSpacingBefore(6);
        return table;
    }

    private void addHeaderRow(PdfPTable table, String[] headers, Font font) {
        BaseColor headerBg = new BaseColor(240, 242, 245);
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, font));
            cell.setBackgroundColor(headerBg);
            cell.setPadding(6);
            table.addCell(cell);
        }
    }

    private void addRow(PdfPTable table, String[] values, Font font) {
        for (String v : values) {
            PdfPCell cell = new PdfPCell(new Phrase(v != null ? v : "-", font));
            cell.setPadding(5);
            table.addCell(cell);
        }
    }

    private void addKvRow(PdfPTable table, String key, String value, Font kFont, Font vFont) {
        PdfPCell kc = new PdfPCell(new Phrase(key, kFont));
        kc.setPadding(5);
        kc.setBackgroundColor(new BaseColor(250, 250, 250));
        table.addCell(kc);
        PdfPCell vc = new PdfPCell(new Phrase(value != null ? value : "-", vFont));
        vc.setPadding(5);
        table.addCell(vc);
    }

    private String str(Object o) {
        return o != null ? String.valueOf(o) : "-";
    }
}
