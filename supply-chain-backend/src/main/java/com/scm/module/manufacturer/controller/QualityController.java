package com.scm.module.manufacturer.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.scm.common.Result;
import com.scm.module.manufacturer.entity.DeviceRecord;
import com.scm.module.manufacturer.entity.QualityReport;
import com.scm.module.manufacturer.entity.RejectRecord;
import com.scm.module.manufacturer.service.DeviceRecordService;
import com.scm.module.manufacturer.service.QualityReportService;
import com.scm.module.manufacturer.service.RejectRecordService;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/manufacturer/quality")
@RequiredArgsConstructor
public class QualityController {

    private final QualityReportService qualityReportService;
    private final DeviceRecordService deviceRecordService;
    private final RejectRecordService rejectRecordService;

    @PostMapping("/report")
    public Result<QualityReport> uploadReport(@RequestBody QualityReport report) {
        LoginUser user = currentUser();
        report.setReporterId(user.getUserId());
        report.setSignerAddr(user.getBlockchainAddr());
        boolean saved = qualityReportService.uploadReport(report);
        return saved ? Result.ok(report) : Result.fail("报告上传失败");
    }

    @PostMapping("/complete")
    public Result<Void> markComplete(@RequestBody Map<String, Object> params) {
        @SuppressWarnings("unchecked")
        List<String> ecids = (List<String>) params.get("ecids");
        if (ecids == null || ecids.isEmpty()) {
            return Result.fail("请选择要标记完成的ECID");
        }
        boolean updated = deviceRecordService.update(new LambdaUpdateWrapper<DeviceRecord>()
                .in(DeviceRecord::getEcid, ecids)
                .set(DeviceRecord::getStatus, "QC_PASS"));
        return updated ? Result.ok() : Result.fail("更新失败");
    }

    @PostMapping("/reject")
    public Result<RejectRecord> reject(@RequestBody RejectRecord record) {
        LoginUser user = currentUser();
        record.setManufacturerId(user.getUserId());
        record.setDisposalStatus("PENDING");

        deviceRecordService.update(new LambdaUpdateWrapper<DeviceRecord>()
                .eq(DeviceRecord::getEcid, record.getEcid())
                .set(DeviceRecord::getStatus, "REJECTED"));

        boolean saved = rejectRecordService.save(record);
        return saved ? Result.ok(record) : Result.fail("记录创建失败");
    }

    @GetMapping("/report/list")
    public Result<List<QualityReport>> listReports(@RequestParam(required = false) String targetType,
                                                   @RequestParam(required = false) String targetId) {
        LambdaQueryWrapper<QualityReport> wrapper = new LambdaQueryWrapper<QualityReport>()
                .eq(targetType != null, QualityReport::getTargetType, targetType)
                .eq(targetId != null, QualityReport::getTargetId, targetId)
                .orderByDesc(QualityReport::getCreateTime);
        List<QualityReport> reports = qualityReportService.list(wrapper);
        return Result.ok(reports);
    }

    private LoginUser currentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
