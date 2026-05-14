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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/distributor/logistics")
@RequiredArgsConstructor
public class LogisticsController {

    private final TransferEventService transferEventService;

    /**
     * 单件发货（扫码/表单录入 SN）：登记一次货权转移与物流信息。
     * <p>
     * 分销商与组装商「渠道流通」前端共用本接口；发货方固定为当前登录用户，
     * 接收方、物流公司、运单号等由请求体 {@link LogisticsShipRequest} 传入。
     * 业务校验与链上锚定等在 {@link TransferEventService#shipTransfer} 中完成。
     * </p>
     *
     * @param req 发货请求（须含 SN、接收方、物流公司与运单号等，见 DTO 字段说明）
     * @return 新建的一条 {@link TransferEvent} 流转记录
     */
    @PostMapping("/ship")
    public Result<TransferEvent> ship(@RequestBody LogisticsShipRequest req) {
        // 当前登录用户作为发货方（sender）
        LoginUser u = getCurrentUser();
        if (req == null) {
            return Result.fail("请求体不能为空");
        }
        // 落库流转事件，并在服务层校验货权、SN 状态及可选上链
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
            @RequestParam(required = false) String estimatedArrivalIso,
            @RequestParam(required = false) String transferType) throws IOException {
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
        String tt = StringUtils.hasText(transferType) ? transferType.trim() : "SHIP";
        List<TransferEvent> list = transferEventService.shipBatch(sns, u.getUserId(), receiverId,
                logisticsCompany, trackingNumber, ship, eta, tt);
        return Result.ok(list);
    }


    /**
     * 收货确认接口：
     * 支持按 transferId、trackingNumber 或 SN 任一条件定位流转记录并完成签收。
     *
     * @param req 收货请求体，需至少包含 transferId、trackingNumber、sn 之一
     * @return 收货确认后的物流流转记录
     */
    @PostMapping("/receive")
    public Result<TransferEvent> receive(@RequestBody LogisticsReceiveRequest req) {
        // 获取当前登录用户，作为收货操作发起人
        LoginUser u = getCurrentUser();
        // 请求体不能为空
        if (req == null) {
            return Result.fail("请求体不能为空");
        }
        // 至少提供一种定位条件：transferId / trackingNumber / sn
        if (req.getTransferId() == null && !StringUtils.hasText(req.getTrackingNumber()) && !StringUtils.hasText(req.getSn())) {
            return Result.fail("请提供 transferId、trackingNumber 或 sn");
        }
        // 调用服务层执行收货并更新流转状态
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
     * 溯源时间轴：按时间混排「发货」「收货确认」，含物流单号、收发方、组装批次、链上哈希摘要。
     */
    @GetMapping("/track/{sn}")
    public Result<List<Map<String, Object>>> track(@PathVariable String sn) {
        List<TransferEvent> events = transferEventService.listBySn(sn);
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (TransferEvent e : events) {
            LocalDateTime shipT = e.getShipTime() != null ? e.getShipTime() : e.getCreateTime();
            if (shipT != null) {
                nodes.add(buildTrackNode(e, "发货", shipT, false));
            }
            if ("RECEIVED".equalsIgnoreCase(e.getStatus()) && e.getActualArrival() != null) {
                nodes.add(buildTrackNode(e, "收货确认", e.getActualArrival(), true));
            }
        }
        nodes.sort(Comparator.comparing(m -> (LocalDateTime) m.get("_sortTime"),
                Comparator.nullsLast(Comparator.naturalOrder())));
        for (Map<String, Object> m : nodes) {
            m.remove("_sortTime");
        }
        return Result.ok(nodes);
    }

    private static Map<String, Object> buildTrackNode(TransferEvent e, String typeLabel,
                                                     LocalDateTime sortTime, boolean receiveLeg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", typeLabel);
        m.put("phase", receiveLeg ? "RECEIVE" : "SHIP");
        m.put("_sortTime", sortTime);
        m.put("time", sortTime.toString());
        String flow = transferTypeLabel(e.getTransferType());
        String desc = receiveLeg
                ? String.format("%s：货权转至用户 %s（原发货方 %s）", flow, e.getReceiverId(), e.getSenderId())
                : String.format("%s：用户 %s → 用户 %s，物流状态 %s", flow, e.getSenderId(), e.getReceiverId(), e.getStatus());
        m.put("description", desc);
        m.put("logisticsNo", e.getTrackingNumber());
        m.put("logisticsCompany", e.getLogisticsCompany());
        if (e.getBatchNo() != null && !e.getBatchNo().isEmpty()) {
            m.put("assemblyBatchNo", e.getBatchNo());
        }
        m.put("txHash", receiveLeg ? e.getReceiveTxHash() : e.getTxHash());
        m.put("raw", e);
        return m;
    }

    private static String transferTypeLabel(String tt) {
        if (tt == null || tt.isEmpty()) {
            return "物流流转";
        }
        switch (tt.toUpperCase()) {
            case "SHIP":
                return "发货出库";
            case "CHANNEL_TO_MFG":
                return "渠道→制造商";
            case "TO_ASSEMBLER":
                return "→组装商";
            case "TO_DISTRIBUTOR":
                return "→分销商";
            case "TO_RETAIL":
                return "→零售/二级渠道";
            default:
                return tt;
        }
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
