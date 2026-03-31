package com.scm.module.assembler.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scm.common.Result;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scm.module.assembler.entity.AssemblyBatch;
import com.scm.module.assembler.entity.AssemblyRecord;
import com.scm.module.assembler.service.AssemblyBatchService;
import com.scm.module.assembler.service.AssemblyRecordService;
import com.scm.module.manufacturer.entity.DeviceRecord;
import com.scm.module.manufacturer.service.DeviceRecordService;
import com.scm.module.system.entity.SysUser;
import com.scm.module.system.service.SysUserService;
import com.scm.security.LoginUser;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/assembler/dashboard")
@RequiredArgsConstructor
public class AssemblerDashboardController {

    private final AssemblyBatchService assemblyBatchService;
    private final AssemblyRecordService assemblyRecordService;
    private final DeviceRecordService deviceRecordService;
    private final SysUserService sysUserService;
    private final ObjectMapper objectMapper;

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        LoginUser user = currentUser();
        Long aid = user.getUserId();

        long batchCount = assemblyBatchService.count(new LambdaQueryWrapper<AssemblyBatch>()
                .eq(AssemblyBatch::getAssemblerId, aid));
        long recordCount = assemblyRecordService.count(new LambdaQueryWrapper<AssemblyRecord>()
                .eq(AssemblyRecord::getAssemblerId, aid));
        long passCount = assemblyRecordService.count(new LambdaQueryWrapper<AssemblyRecord>()
                .eq(AssemblyRecord::getAssemblerId, aid)
                .eq(AssemblyRecord::getTestResult, "PASS"));
        long failCount = assemblyRecordService.count(new LambdaQueryWrapper<AssemblyRecord>()
                .eq(AssemblyRecord::getAssemblerId, aid)
                .eq(AssemblyRecord::getTestResult, "FAIL"));
        long withReport = assemblyRecordService.count(new LambdaQueryWrapper<AssemblyRecord>()
                .eq(AssemblyRecord::getAssemblerId, aid)
                .isNotNull(AssemblyRecord::getTestReportHash));
        long onChain = assemblyRecordService.count(new LambdaQueryWrapper<AssemblyRecord>()
                .eq(AssemblyRecord::getAssemblerId, aid)
                .eq(AssemblyRecord::getChainRegistered, 1));

        long decided = passCount + failCount;
        double qcRate = decided > 0 ? (passCount * 100.0 / decided) : 0;
        long componentsConsumed = assemblyRecordService.sumEcidSlots(aid);

        List<Long> last7 = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            LocalDateTime start = day.atStartOfDay();
            LocalDateTime end = LocalDateTime.of(day.plusDays(1), LocalTime.MIN);
            long c = assemblyRecordService.count(new LambdaQueryWrapper<AssemblyRecord>()
                    .eq(AssemblyRecord::getAssemblerId, aid)
                    .ge(AssemblyRecord::getCreateTime, start)
                    .lt(AssemblyRecord::getCreateTime, end));
            last7.add(c);
        }

        Map<String, Object> pie = new HashMap<>();
        pie.put("pass", passCount);
        pie.put("fail", failCount);
        pie.put("pending", Math.max(0, recordCount - passCount - failCount));

        Map<String, Object> data = new HashMap<>();
        data.put("batchCount", batchCount);
        data.put("recordCount", recordCount);
        data.put("componentsConsumed", componentsConsumed);
        data.put("withQcReport", withReport);
        data.put("onChainRecords", onChain);
        data.put("qcPassRatePercent", Math.round(qcRate * 10) / 10.0);
        data.put("last7DaysAssembled", last7);
        data.put("qcPie", pie);
        return Result.ok(data);
    }

    /**
     * PDF：组装溯源树预览（ECharts tree 数据）。
     */
    @GetMapping("/sn-tree")
    public Result<Map<String, Object>> snTree(@RequestParam String sn) {
        LoginUser user = currentUser();
        AssemblyRecord record = assemblyRecordService.listBySn(sn);
        if (record == null) {
            return Result.fail("未找到该 SN 的组装记录");
        }
        if (!user.getUserId().equals(record.getAssemblerId())) {
            return Result.fail("无权查看该 SN 的组装数据");
        }
        List<String> ecids = parseEcids(record.getEcidList());
        Map<String, DeviceRecord> byEcid = Collections.emptyMap();
        if (!ecids.isEmpty()) {
            List<DeviceRecord> devices = deviceRecordService.list(new LambdaQueryWrapper<DeviceRecord>()
                    .in(DeviceRecord::getEcid, ecids));
            byEcid = devices.stream()
                    .filter(d -> d.getEcid() != null)
                    .collect(Collectors.toMap(DeviceRecord::getEcid, d -> d, (a, b) -> a));
        }
        Set<Long> mIds = byEcid.values().stream()
                .map(DeviceRecord::getManufacturerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        Map<Long, SysUser> manufacturers = new HashMap<>();
        for (Long mid : mIds) {
            SysUser u = sysUserService.getById(mid);
            if (u != null) {
                manufacturers.put(mid, u);
            }
        }
        List<Map<String, Object>> children = new ArrayList<>();
        for (String ecid : ecids) {
            DeviceRecord dr = byEcid.get(ecid);
            Map<String, Object> leaf = new HashMap<>();
            if (dr != null) {
                String dtype = dr.getDeviceType();
                String mname = null;
                if (dr.getManufacturerId() != null) {
                    SysUser u = manufacturers.get(dr.getManufacturerId());
                    if (u != null) {
                        mname = StringUtils.hasText(u.getEnterpriseName()) ? u.getEnterpriseName() : u.getUsername();
                    }
                }
                String label = ecid;
                if (StringUtils.hasText(dtype)) {
                    label = ecid + " · " + dtype;
                }
                leaf.put("name", label);
                leaf.put("value", ecid);
                leaf.put("deviceType", dtype);
                leaf.put("manufacturer", mname);
            } else {
                leaf.put("name", ecid);
                leaf.put("value", ecid);
            }
            children.add(leaf);
        }
        Map<String, Object> root = new HashMap<>();
        root.put("name", record.getSn());
        root.put("children", children);
        Map<String, Object> out = new HashMap<>();
        out.put("tree", root);
        out.put("firmwareVersion", record.getFirmwareVersion());
        out.put("assemblyBatchNo", record.getAssemblyBatchNo());
        return Result.ok(out);
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

    private LoginUser currentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
