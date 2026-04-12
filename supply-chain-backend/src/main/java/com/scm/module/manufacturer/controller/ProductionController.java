package com.scm.module.manufacturer.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scm.common.PageResult;
import com.scm.common.Result;
import com.scm.module.manufacturer.dto.DeviceRegisterRequest;
import com.scm.module.manufacturer.entity.DeviceRecord;
import com.scm.module.manufacturer.entity.ManufacturingAgreement;
import com.scm.module.manufacturer.entity.ProductionBatch;
import com.scm.module.manufacturer.service.DeviceRecordService;
import com.scm.module.manufacturer.service.ManufacturingAgreementService;
import com.scm.module.manufacturer.service.ProductionBatchService;
import com.scm.module.supplier.entity.BomItem;
import com.scm.module.supplier.entity.ProductionRequest;
import com.scm.module.supplier.entity.Bom;
import com.scm.module.supplier.service.BomService;
import com.scm.module.supplier.service.ProductionRequestService;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/manufacturer/production")
@RequiredArgsConstructor
public class ProductionController {

    private final ProductionBatchService batchService;
    private final DeviceRecordService deviceRecordService;
    private final ProductionRequestService productionRequestService;
    private final ManufacturingAgreementService manufacturingAgreementService;
    private final BomService bomService;

    //创建生产批次
    @PostMapping("/batch")
    public Result<ProductionBatch> createBatch(@RequestBody Map<String, Object> params) {
        LoginUser user = currentUser();
        //订单id
        String orderId = (String) params.get("orderId");
        //计划制造数量
        Object q = params.get("qty");
        if (q == null) {
            q = params.get("plannedQty");
        }
        Integer qty = null;
        if (q instanceof Number) {
            qty = ((Number) q).intValue();
        } else if (q != null) {
            try {
                qty = Integer.parseInt(String.valueOf(q));
            } catch (NumberFormatException ignored) {
                /* fall through */
            }
        }
        if (orderId == null || orderId.trim().isEmpty() || qty == null || qty <= 0) {
            return Result.fail("参数不完整（需 orderId 与 qty/plannedQty）");
        }
        Long bomItemId = parseLongParam(params.get("bomItemId"));
        ProductionBatch batch = batchService.createBatch(orderId.trim(), user.getUserId(), qty, bomItemId);
        return Result.ok(batch);
    }

    /**
     * 已接单制造商查看订单 BOM 明细，用于创建「子件批次」。
     */
    @GetMapping("/order/{orderId}/bom-items")
    public Result<List<BomItem>> listBomItemsForProductionOrder(@PathVariable String orderId) {
        LoginUser user = currentUser();
        ProductionRequest order = productionRequestService.getOne(
                new LambdaQueryWrapper<ProductionRequest>()
                        .eq(ProductionRequest::getOrderId, orderId.trim()));
        if (order == null) {
            return Result.fail("订单不存在");
        }
        long agreed = manufacturingAgreementService.count(
                new LambdaQueryWrapper<ManufacturingAgreement>()
                        .eq(ManufacturingAgreement::getOrderId, order.getOrderId())
                        .eq(ManufacturingAgreement::getManufacturerId, user.getUserId()));
        if (agreed == 0) {
            return Result.fail("无权查看该订单的 BOM");
        }
        if (order.getBomId() == null) {
            return Result.ok(Collections.emptyList());
        }
        Bom bom = bomService.getBomWithItems(order.getBomId());
        List<BomItem> items = bom.getItems();
        return Result.ok(items != null ? items : Collections.emptyList());
    }

    private static Long parseLongParam(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number) {
            return ((Number) raw).longValue();
        }
        String s = String.valueOf(raw).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @PostMapping("/batch/complete")
    public Result<Void> completeBatch(@RequestBody Map<String, String> body) {
        String batchId = body != null ? body.get("batchId") : null;
        if (batchId == null || batchId.trim().isEmpty()) {
            return Result.fail("batchId 不能为空");
        }
        batchService.completeBatch(batchId.trim(), currentUser().getUserId());
        return Result.ok();
    }

    @GetMapping("/batch/list")
    public Result<PageResult<ProductionBatch>> listBatches(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        LoginUser user = currentUser();
        Page<ProductionBatch> p = new Page<>(page, pageSize);
        IPage<ProductionBatch> raw = batchService.pageByManufacturer(user.getUserId(), p);
        PageResult<ProductionBatch> pr = new PageResult<ProductionBatch>()
                .setRecords(raw.getRecords())
                .setTotal(raw.getTotal())
                .setCurrent(raw.getCurrent())
                .setSize(raw.getSize());
        return Result.ok(pr);
    }

    /**
     * 批量生产ECID
     * @param params 批次id、数量、设备类型
     * @return
     */
    @PostMapping("/ecid/generate")
    public Result<List<String>> generateEcids(@RequestBody Map<String, Object> params) {
        LoginUser user = currentUser();
        String batchId = (String) params.get("batchId");
        String orderId = params.get("orderId") != null ? String.valueOf(params.get("orderId")) : null;
        Object q = params.get("qty");
        if (q == null) {
            q = params.get("quantity");
        }
        Integer qty = q instanceof Number ? ((Number) q).intValue() : null;
        if (q != null && qty == null) {
            try {
                qty = Integer.parseInt(String.valueOf(q));
            } catch (NumberFormatException ignored) {
                /* ignore */
            }
        }
        String deviceType = params.get("deviceType") != null ? String.valueOf(params.get("deviceType")) : null;
        if (batchId == null || batchId.trim().isEmpty() || qty == null || qty <= 0) {
            return Result.fail("参数不完整");
        }
        batchId = batchId.trim();
        //批量生成ECID
        if (orderId == null || orderId.trim().isEmpty()) {
            return Result.ok(deviceRecordService.generateEcidsForBatch(batchId, user.getUserId(), qty, deviceType));
        }
        return Result.ok(deviceRecordService.generateEcids(batchId, orderId.trim(), user.getUserId(), qty, deviceType));
    }

    @GetMapping("/ecid/list")
    public Result<PageResult<DeviceRecord>> listEcids(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String batchId) {
        LoginUser user = currentUser();
        Page<DeviceRecord> p = new Page<>(page, pageSize);
        IPage<DeviceRecord> raw = deviceRecordService.pageForManufacturer(user.getUserId(), p, batchId);
        PageResult<DeviceRecord> pr = new PageResult<DeviceRecord>()
                .setRecords(raw.getRecords())
                .setTotal(raw.getTotal())
                .setCurrent(raw.getCurrent())
                .setSize(raw.getSize());
        return Result.ok(pr);
    }

    /**
     * 上链注册设备功能
     * @param request 设备记录id列表
     * @return
     */
    @PostMapping("/ecid/register")
    public Result<Void> registerOnChain(@RequestBody DeviceRegisterRequest request) {
        LoginUser user = currentUser();
        if (request == null
                || ((request.getIds() == null || request.getIds().isEmpty())
                && (request.getEcids() == null || request.getEcids().isEmpty()))) {
            return Result.fail("请提交 ids 或 ecids");
        }
        //对设备记录调用anchor逐条上链
        boolean success = deviceRecordService.registerOnChain(request, user.getUserId());
        return success ? Result.ok() : Result.fail("上链注册失败");
    }

    /**
     * 车间打码导出：CSV（UTF-8 BOM），可按批次或按 ECID 列表。
     */
    @GetMapping("/ecid/export-file")
    public void exportEcidsFile(
            @RequestParam(required = false) String batchId,
            @RequestParam(required = false) String ecids,
            HttpServletResponse response) throws IOException {
        LoginUser user = currentUser();
        if (!StringUtils.hasText(batchId) && !StringUtils.hasText(ecids)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        LambdaQueryWrapper<DeviceRecord> w = new LambdaQueryWrapper<DeviceRecord>()
                .eq(DeviceRecord::getManufacturerId, user.getUserId());
        if (StringUtils.hasText(batchId)) {
            w.eq(DeviceRecord::getBatchId, batchId.trim());
        }
        if (StringUtils.hasText(ecids)) {
            String[] parts = ecids.split(",");
            java.util.List<String> ecidList = new java.util.ArrayList<>();
            for (String p : parts) {
                if (p != null && !p.trim().isEmpty()) {
                    ecidList.add(p.trim());
                }
            }
            if (!ecidList.isEmpty()) {
                w.in(DeviceRecord::getEcid, ecidList);
            }
        }
        w.orderByAsc(DeviceRecord::getEcid);
        List<DeviceRecord> records = deviceRecordService.list(w);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/csv;charset=UTF-8");
        String fn = java.net.URLEncoder.encode("ecid-export.csv", StandardCharsets.UTF_8.name()).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + fn);
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {
            pw.write('\uFEFF');
            pw.println("ecid,orderId,batchId,bomItemId,deviceType,status,chainRegistered");
            for (DeviceRecord r : records) {
                pw.printf("%s,%s,%s,%s,%s,%s,%s%n",
                        csv(r.getEcid()),
                        csv(r.getOrderId()),
                        csv(r.getBatchId()),
                        r.getBomItemId() == null ? "" : String.valueOf(r.getBomItemId()),
                        csv(r.getDeviceType()),
                        csv(r.getStatus()),
                        r.getChainRegistered() != null && r.getChainRegistered() == 1 ? "1" : "0");
            }
        }
    }

    private static String csv(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replace("\"", "\"\"");
        if (t.contains(",") || t.contains("\"") || t.contains("\n")) {
            return "\"" + t + "\"";
        }
        return t;
    }

    private LoginUser currentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
