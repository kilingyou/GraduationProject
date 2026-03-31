package com.scm.module.manufacturer.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scm.common.PageResult;
import com.scm.common.Result;
import com.scm.common.exception.BusinessException;
import com.scm.common.util.HashUtil;
import com.scm.integration.blockchain.BlockchainAnchorService;
import com.scm.module.manufacturer.entity.DeviceRecord;
import com.scm.module.manufacturer.entity.QualityReport;
import com.scm.module.manufacturer.entity.RejectRecord;
import com.scm.module.manufacturer.service.DeviceRecordService;
import com.scm.module.manufacturer.service.QualityReportService;
import com.scm.module.manufacturer.service.RejectRecordService;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/manufacturer/quality")
@RequiredArgsConstructor
public class QualityController {

    private final QualityReportService qualityReportService;
    private final DeviceRecordService deviceRecordService;
    private final RejectRecordService rejectRecordService;
    private final BlockchainAnchorService blockchainAnchorService;

    @PostMapping(value = "/report", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<QualityReport> uploadReport(
            @RequestPart("file") MultipartFile file,
            @RequestParam String targetType,
            @RequestParam String targetId,
            @RequestParam(required = false, defaultValue = "PASS") String result,
            @RequestParam(required = false) String reportName,
            @RequestParam(required = false) String remark) throws IOException {
        LoginUser user = currentUser();
        if (file == null || file.isEmpty()) {
            return Result.fail("请上传报告文件");
        }
        QualityReport report = new QualityReport();
        report.setReporterId(user.getUserId());
        report.setSignerAddr(user.getBlockchainAddr());
        report.setTargetType(targetType);
        report.setTargetId(targetId);
        report.setResult(result);
        report.setReportName(reportName);
        report.setRemark(remark);
        QualityReport saved = qualityReportService.saveManufacturedReport(
                report, file.getBytes(), file.getOriginalFilename());
        return Result.ok(saved);
    }

    @PostMapping("/complete")
    public Result<Void> markComplete(@RequestBody Map<String, Object> params) {
        String targetType = (String) params.get("targetType");
        String targetId = (String) params.get("targetId");
        if (targetType == null || targetId == null || targetId.trim().isEmpty()) {
            return Result.fail("请提供 targetType 与 targetId");
        }
        LoginUser user = currentUser();
        List<String> ecids = resolveEcids(targetType, targetId, user.getUserId());
        String anchorPayload = String.join(",", ecids) + "|QC_PASS";
        blockchainAnchorService.anchor("MFG_QC_PASS", HashUtil.sha256Hex(anchorPayload));
        boolean updated = deviceRecordService.update(new LambdaUpdateWrapper<DeviceRecord>()
                .in(DeviceRecord::getEcid, ecids)
                .set(DeviceRecord::getStatus, "QC_PASS"));
        return updated ? Result.ok() : Result.fail("更新失败");
    }

    @PostMapping("/reject")
    public Result<Void> reject(@RequestBody Map<String, Object> body) {
        String targetType = (String) body.get("targetType");
        String targetId = (String) body.get("targetId");
        String reason = (String) body.get("reason");
        if (targetType == null || targetId == null || targetId.trim().isEmpty()) {
            return Result.fail("请提供 targetType 与 targetId");
        }
        if (reason == null || reason.trim().isEmpty()) {
            return Result.fail("请填写不合格原因");
        }
        LoginUser user = currentUser();
        List<String> ecids = resolveEcids(targetType, targetId, user.getUserId());
        String anchorPayload = String.join(",", ecids) + "|" + reason;
        String txHash = blockchainAnchorService.anchor("MFG_REJECT", HashUtil.sha256Hex(anchorPayload));

        deviceRecordService.update(new LambdaUpdateWrapper<DeviceRecord>()
                .in(DeviceRecord::getEcid, ecids)
                .set(DeviceRecord::getStatus, "REJECTED"));

        for (String ecid : ecids) {
            RejectRecord record = new RejectRecord();
            record.setEcid(ecid);
            if ("BATCH".equalsIgnoreCase(targetType)) {
                record.setBatchId(targetId);
            }
            record.setManufacturerId(user.getUserId());
            record.setReason(reason);
            record.setDisposalStatus("PENDING");
            record.setTxHash(txHash);
            rejectRecordService.save(record);
        }
        return Result.ok();
    }

    @GetMapping("/report/list")
    public Result<PageResult<QualityReport>> listReports(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId) {
        LoginUser user = currentUser();
        Page<QualityReport> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<QualityReport> wrapper = new LambdaQueryWrapper<QualityReport>()
                .eq(QualityReport::getReporterId, user.getUserId())
                .eq(targetType != null && !targetType.trim().isEmpty(), QualityReport::getTargetType, targetType)
                .eq(targetId != null && !targetId.trim().isEmpty(), QualityReport::getTargetId, targetId)
                .orderByDesc(QualityReport::getCreateTime);
        IPage<QualityReport> result = qualityReportService.page(p, wrapper);
        PageResult<QualityReport> pageResult = new PageResult<QualityReport>()
                .setRecords(result.getRecords())
                .setTotal(result.getTotal())
                .setCurrent(result.getCurrent())
                .setSize(result.getSize());
        return Result.ok(pageResult);
    }

    private List<String> resolveEcids(String targetType, String targetId, Long manufacturerId) {
        if ("ECID".equalsIgnoreCase(targetType)) {
            DeviceRecord one = deviceRecordService.getOne(new LambdaQueryWrapper<DeviceRecord>()
                    .eq(DeviceRecord::getEcid, targetId)
                    .eq(DeviceRecord::getManufacturerId, manufacturerId));
            if (one == null) {
                throw new BusinessException("设备不存在或无权限");
            }
            return Collections.singletonList(targetId);
        }
        if ("BATCH".equalsIgnoreCase(targetType)) {
            List<DeviceRecord> list = deviceRecordService.list(new LambdaQueryWrapper<DeviceRecord>()
                    .eq(DeviceRecord::getBatchId, targetId)
                    .eq(DeviceRecord::getManufacturerId, manufacturerId));
            if (list.isEmpty()) {
                throw new BusinessException("批次下无设备或无权限");
            }
            return list.stream().map(DeviceRecord::getEcid).collect(Collectors.toList());
        }
        throw new BusinessException("不支持的 targetType: " + targetType);
    }

    private LoginUser currentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
