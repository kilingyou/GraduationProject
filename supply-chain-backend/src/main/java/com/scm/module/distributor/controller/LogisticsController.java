package com.scm.module.distributor.controller;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scm.common.PageResult;
import com.scm.common.Result;
import com.scm.module.distributor.dto.LogisticsReceiveRequest;
import com.scm.module.distributor.dto.LogisticsShipRequest;
import com.scm.module.distributor.dto.SnImportRow;
import com.scm.module.distributor.entity.TransferEvent;
import com.scm.module.distributor.service.TransferEventService;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/distributor/logistics")
@RequiredArgsConstructor
public class LogisticsController {

    private final TransferEventService transferEventService;

    @PostMapping("/ship")
    public Result<TransferEvent> ship(@RequestBody LogisticsShipRequest req) {
        LoginUser u = getCurrentUser();
        if (req == null) {
            return Result.fail("请求体不能为空");
        }
        TransferEvent created = transferEventService.shipTransfer(
                req.getSn(),
                u.getUserId(),
                req.getReceiverId(),
                req.getLogisticsCompany(),
                req.getTrackingNumber(),
                req.getShipTime(),
                req.getEstimatedArrival(),
                req.getTransferType());
        return Result.ok(created);
    }

    /**
     * Excel 批量发货：表头「SN」，共用同一物流信息（符合装箱单场景）。
     */
    @PostMapping(value = "/ship-batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<List<TransferEvent>> shipBatch(
            @RequestPart("file") MultipartFile file,
            @RequestParam String logisticsCompany,
            @RequestParam String trackingNumber,
            @RequestParam Long receiverId,
            @RequestParam(required = false) String shipTimeIso,
            @RequestParam(required = false) String estimatedArrivalIso) throws IOException {
        LoginUser u = getCurrentUser();
        if (file == null || file.isEmpty()) {
            return Result.fail("请上传文件");
        }
        List<SnImportRow> rows = EasyExcel.read(file.getInputStream()).head(SnImportRow.class).sheet().doReadSync();
        List<String> sns = new ArrayList<>();
        for (SnImportRow row : rows) {
            if (row.getSn() != null && !row.getSn().trim().isEmpty()) {
                sns.add(row.getSn().trim());
            }
        }
        if (sns.isEmpty()) {
            return Result.fail("未解析到 SN，请使用表头为「SN」的列");
        }
        java.time.LocalDateTime ship = null;
        java.time.LocalDateTime eta = null;
        if (StringUtils.hasText(shipTimeIso)) {
            ship = java.time.LocalDateTime.ofInstant(java.time.Instant.parse(shipTimeIso), java.time.ZoneId.systemDefault());
        }
        if (StringUtils.hasText(estimatedArrivalIso)) {
            eta = java.time.LocalDateTime.ofInstant(java.time.Instant.parse(estimatedArrivalIso), java.time.ZoneId.systemDefault());
        }
        List<TransferEvent> list = transferEventService.shipBatch(sns, u.getUserId(), receiverId,
                logisticsCompany, trackingNumber, ship, eta, "SHIP");
        return Result.ok(list);
    }

    @PostMapping("/receive")
    public Result<TransferEvent> receive(@RequestBody LogisticsReceiveRequest req) {
        LoginUser u = getCurrentUser();
        if (req == null) {
            return Result.fail("请求体不能为空");
        }
        if (req.getTransferId() == null && !StringUtils.hasText(req.getTrackingNumber()) && !StringUtils.hasText(req.getSn())) {
            return Result.fail("请提供 transferId、trackingNumber 或 sn");
        }
        TransferEvent updated = transferEventService.receiveTransfer(
                u.getUserId(), req.getTransferId(), req.getTrackingNumber(), req.getSn());
        return Result.ok(updated);
    }

    @GetMapping("/list")
    public Result<PageResult<TransferEvent>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        LoginUser u = getCurrentUser();
        int pn = pageNum != null && pageNum > 0 ? pageNum : (page != null && page > 0 ? page : 1);
        int ps = pageSize != null && pageSize > 0 ? pageSize : (size != null && size > 0 ? size : 10);
        Page<TransferEvent> transferPage = new Page<>(pn, ps);
        IPage<TransferEvent> raw = transferEventService.listForParticipant(u.getUserId(), transferPage);
        PageResult<TransferEvent> pageResult = new PageResult<TransferEvent>()
                .setRecords(raw.getRecords())
                .setTotal(raw.getTotal())
                .setCurrent(raw.getCurrent())
                .setSize(raw.getSize());
        return Result.ok(pageResult);
    }

    /**
     * 前端时间轴：标准化字段 type / time / description / logisticsNo
     */
    @GetMapping("/track/{sn}")
    public Result<List<Map<String, Object>>> track(@PathVariable String sn) {
        List<TransferEvent> events = transferEventService.listBySn(sn);
        List<Map<String, Object>> out = new ArrayList<>();
        for (TransferEvent e : events) {
            Map<String, Object> m = new LinkedHashMap<>();
            String typeLabel = "SHIP".equalsIgnoreCase(e.getTransferType()) ? "发货" : (e.getTransferType() != null ? e.getTransferType() : "流转");
            m.put("type", typeLabel);
            m.put("time", e.getShipTime() != null ? e.getShipTime().toString()
                    : (e.getCreateTime() != null ? e.getCreateTime().toString() : ""));
            String desc = String.format("发件人ID %s → 收件人ID %s，状态 %s",
                    e.getSenderId(), e.getReceiverId(), e.getStatus());
            m.put("description", desc);
            m.put("logisticsNo", e.getTrackingNumber());
            m.put("logisticsCompany", e.getLogisticsCompany());
            m.put("raw", e);
            out.add(m);
        }
        return Result.ok(out);
    }

    @GetMapping("/sn-import-template")
    public void snImportTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fn = java.net.URLEncoder.encode("SN批量发货模板", java.nio.charset.StandardCharsets.UTF_8.name()).replace("+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fn + ".xlsx");
        SnImportRow demo = new SnImportRow();
        demo.setSn("SN-示例-请删除");
        EasyExcel.write(response.getOutputStream(), SnImportRow.class).sheet("SN").doWrite(Collections.singletonList(demo));
    }

    private LoginUser getCurrentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
