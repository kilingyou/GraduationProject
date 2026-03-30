package com.scm.module.assembler.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scm.common.Result;
import com.scm.module.manufacturer.entity.DeviceRecord;
import com.scm.module.manufacturer.service.DeviceRecordService;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/assembler/intake")
@RequiredArgsConstructor
public class IntakeController {

    private final DeviceRecordService deviceRecordService;

    @PostMapping("/scan")
    public Result<DeviceRecord> scan(@RequestBody Map<String, String> body) {
        String ecid = body.get("ecid");
        if (ecid == null || ecid.isEmpty()) {
            return Result.fail("ECID is required");
        }

        DeviceRecord device = deviceRecordService.getOne(new LambdaQueryWrapper<DeviceRecord>()
                .eq(DeviceRecord::getEcid, ecid));
        if (device == null) {
            return Result.fail("Device not found for ECID: " + ecid);
        }
        if (!"QC_PASS".equals(device.getStatus())) {
            return Result.fail("Device status is not QC_PASS, current: " + device.getStatus());
        }
        return Result.ok(device);
    }

    @PostMapping("/batch-import")
    public Result<List<DeviceRecord>> batchImport(@RequestBody Map<String, List<String>> body) {
        List<String> ecids = body.get("ecids");
        if (ecids == null || ecids.isEmpty()) {
            return Result.fail("ECID list is required");
        }

        List<DeviceRecord> results = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        for (String ecid : ecids) {
            DeviceRecord device = deviceRecordService.getOne(new LambdaQueryWrapper<DeviceRecord>()
                    .eq(DeviceRecord::getEcid, ecid));
            if (device == null) {
                failures.add(ecid + " not found");
            } else if (!"QC_PASS".equals(device.getStatus())) {
                failures.add(ecid + " status is " + device.getStatus());
            } else {
                results.add(device);
            }
        }

        if (!failures.isEmpty()) {
            return Result.fail("Some ECIDs failed validation: " + String.join(", ", failures));
        }
        return Result.ok(results);
    }

    private LoginUser getCurrentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
