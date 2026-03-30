package com.scm.module.manufacturer.controller;

import com.scm.common.Result;
import com.scm.module.manufacturer.entity.DeviceRecord;
import com.scm.module.manufacturer.entity.ProductionBatch;
import com.scm.module.manufacturer.service.DeviceRecordService;
import com.scm.module.manufacturer.service.ProductionBatchService;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/manufacturer/production")
@RequiredArgsConstructor
public class ProductionController {

    private final ProductionBatchService batchService;
    private final DeviceRecordService deviceRecordService;

    @PostMapping("/batch")
    public Result<ProductionBatch> createBatch(@RequestBody Map<String, Object> params) {
        LoginUser user = currentUser();
        String orderId = (String) params.get("orderId");
        Integer qty = (Integer) params.get("qty");
        if (orderId == null || qty == null || qty <= 0) {
            return Result.fail("参数不完整");
        }
        ProductionBatch batch = batchService.createBatch(orderId, user.getUserId(), qty);
        return Result.ok(batch);
    }

    @GetMapping("/batch/list")
    public Result<List<ProductionBatch>> listBatches() {
        LoginUser user = currentUser();
        List<ProductionBatch> batches = batchService.listByManufacturer(user.getUserId());
        return Result.ok(batches);
    }

    @PostMapping("/ecid/generate")
    public Result<List<String>> generateEcids(@RequestBody Map<String, Object> params) {
        LoginUser user = currentUser();
        String batchId = (String) params.get("batchId");
        String orderId = (String) params.get("orderId");
        Integer qty = params.get("qty") != null ? ((Number) params.get("qty")).intValue() : null;
        String deviceType = (String) params.get("deviceType");
        if (batchId == null || orderId == null || qty == null || qty <= 0) {
            return Result.fail("参数不完整");
        }
        List<String> ecids = deviceRecordService.generateEcids(batchId, orderId, user.getUserId(), qty, deviceType);
        return Result.ok(ecids);
    }

    @GetMapping("/ecid/list")
    public Result<List<DeviceRecord>> listEcids(@RequestParam String batchId) {
        List<DeviceRecord> records = deviceRecordService.listByBatch(batchId);
        return Result.ok(records);
    }

    @PostMapping("/ecid/export")
    public Result<List<DeviceRecord>> exportEcids(@RequestBody Map<String, String> params) {
        String batchId = params.get("batchId");
        if (batchId == null) {
            return Result.fail("batchId不能为空");
        }
        List<DeviceRecord> records = deviceRecordService.listByBatch(batchId);
        return Result.ok(records);
    }

    @PostMapping("/ecid/register")
    public Result<Void> registerOnChain(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.fail("请选择要上链的设备");
        }
        boolean success = deviceRecordService.registerOnChain(ids);
        return success ? Result.ok() : Result.fail("上链注册失败");
    }

    private LoginUser currentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
