package com.scm.module.manufacturer.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scm.common.Constants;
import com.scm.common.PageResult;
import com.scm.common.Result;
import com.scm.common.exception.BusinessException;
import com.scm.common.util.HashUtil;
import com.scm.integration.blockchain.BlockchainAnchorService;
import com.scm.module.manufacturer.dto.RejectRecordVO;
import com.scm.module.manufacturer.entity.DeviceRecord;
import com.scm.module.manufacturer.entity.QualityReport;
import com.scm.module.manufacturer.entity.RejectRecord;
import com.scm.module.manufacturer.service.DeviceRecordService;
import com.scm.module.manufacturer.service.ProductionBatchService;
import com.scm.module.manufacturer.service.QualityReportService;
import com.scm.module.manufacturer.service.RejectDispositionService;
import com.scm.module.manufacturer.service.RejectRecordService;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
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
    private final ProductionBatchService productionBatchService;
    private final RejectRecordService rejectRecordService;
    private final RejectDispositionService rejectDispositionService;
    private final BlockchainAnchorService blockchainAnchorService;

    @PostMapping(value = "/report", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<QualityReport> uploadReport(
            @RequestPart("file") MultipartFile file,
            @RequestParam String targetType,
            @RequestParam String targetId,
            @RequestParam(required = false, defaultValue = "PASS") String result,
            @RequestParam(required = false) String reportName,
            @RequestParam(required = false) String remark) throws IOException {

        // 获取当前登录用户信息，后续用于记录报告上传人和区块链签名地址
        LoginUser user = currentUser();

        // 校验上传文件是否为空
        if (file == null || file.isEmpty()) {
            return Result.fail("请上传报告文件");
        }

        // 构造质检报告对象，并写入基础信息
        QualityReport report = new QualityReport();
        report.setReporterId(user.getUserId());              // 报告上传人ID
        report.setSignerAddr(user.getBlockchainAddr());      // 上传人区块链地址
        report.setTargetType(targetType);                    // 报告关联对象类型，例如 ECID 或 BATCH
        report.setTargetId(targetId);                        // 报告关联对象ID
        report.setResult(result);                            // 检测结果，默认 PASS
        report.setReportName(reportName);                    // 报告名称
        report.setRemark(remark);                            // 备注信息

        // 保存质检报告，并上传文件内容
        QualityReport saved = qualityReportService.saveManufacturedReport(
                report, file.getBytes(), file.getOriginalFilename());

        // 如果目标类型和目标ID存在，则进一步尝试把报告信息回写到设备记录中
        if (StringUtils.hasText(targetType) && StringUtils.hasText(targetId)) {
            // 去除目标类型两端空格，避免比较时受空白字符影响
            String tt = targetType.trim();

            // 仅当目标类型为 ECID 或 BATCH 时，才处理设备记录关联
            if ("ECID".equalsIgnoreCase(tt) || "BATCH".equalsIgnoreCase(tt)) {
                // 根据目标类型和目标ID解析出对应的 ECID 列表
                List<String> ecids = resolveEcids(tt, targetId.trim(), user.getUserId());

                // 若存在设备记录，且报告文件哈希不为空，则将报告哈希和 IPFS CID 更新到设备记录中
                if (!ecids.isEmpty() && StringUtils.hasText(saved.getFileHash())) {
                    deviceRecordService.update(new LambdaUpdateWrapper<DeviceRecord>()
                            .in(DeviceRecord::getEcid, ecids)
                            .eq(DeviceRecord::getManufacturerId, user.getUserId())
                            .set(DeviceRecord::getTestReportHash, saved.getFileHash())
                            .set(DeviceRecord::getTestReportCid, saved.getIpfsCid()));
                }
            }
        }

        // 返回保存后的质检报告信息
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
        // 批次级合格：不得覆盖已单独标记为不合格的 ECID；单台 ECID 合格仍允许（误判纠正）
        if ("BATCH".equalsIgnoreCase(targetType)) {
            ecids = deviceRecordService.list(new LambdaQueryWrapper<DeviceRecord>()
                            .in(DeviceRecord::getEcid, ecids)
                            .eq(DeviceRecord::getManufacturerId, user.getUserId())
                            .ne(DeviceRecord::getStatus, Constants.REJECTED))
                    .stream()
                    .map(DeviceRecord::getEcid)
                    .collect(Collectors.toList());
            if (ecids.isEmpty()) {
                return Result.fail("本批次无待标记设备，或设备均已标记为不合格");
            }
        }
        for (String ecid : ecids) {
            DeviceRecord dev = deviceRecordService.getOne(new LambdaQueryWrapper<DeviceRecord>()
                    .eq(DeviceRecord::getEcid, ecid)
                    .eq(DeviceRecord::getManufacturerId, user.getUserId()));
            if (dev == null || !StringUtils.hasText(dev.getTestReportHash())) {
                return Result.fail("存在未上传检测报告的设备，请先上传报告并写入哈希后再标记合格: " + ecid);
            }
        }
        String anchorPayload = String.join(",", ecids) + "|QC_PASS";
        blockchainAnchorService.anchor("MFG_QC_PASS", HashUtil.sha256Hex(anchorPayload));
        boolean updated = deviceRecordService.update(new LambdaUpdateWrapper<DeviceRecord>()
                .in(DeviceRecord::getEcid, ecids)
                .set(DeviceRecord::getStatus, Constants.QC_PASS));
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
        String disposalType = normalizeDisposalType(body.get("disposalType"));
        if (disposalType == null) {
            return Result.fail("请选择处置方式：退货(RETURN) 或 销毁(DESTROY)");
        }
        String anchorPayload = String.join(",", ecids) + "|" + reason + "|" + disposalType;
        String txHash = blockchainAnchorService.anchor("MFG_REJECT", HashUtil.sha256Hex(anchorPayload));

        deviceRecordService.update(new LambdaUpdateWrapper<DeviceRecord>()
                .in(DeviceRecord::getEcid, ecids)
                .set(DeviceRecord::getStatus, Constants.REJECTED));
        String disposalStatus = Constants.DISPOSAL_RETURN.equals(disposalType)
                ? Constants.DISPOSAL_AWAITING_SUPPLIER
                : Constants.DISPOSAL_AWAITING_MFG_DESTROY;

        for (String ecid : ecids) {
            DeviceRecord dev = deviceRecordService.getOne(new LambdaQueryWrapper<DeviceRecord>()
                    .eq(DeviceRecord::getEcid, ecid)
                    .eq(DeviceRecord::getManufacturerId, user.getUserId()));
            RejectRecord record = new RejectRecord();
            record.setEcid(ecid);
            if ("BATCH".equalsIgnoreCase(targetType)) {
                record.setBatchId(targetId);
            }
            record.setManufacturerId(user.getUserId());
            record.setOrderId(dev != null ? dev.getOrderId() : null);
            record.setReason(reason);
            record.setDisposalType(disposalType);
            record.setDisposalStatus(disposalStatus);
            record.setTxHash(txHash);
            rejectRecordService.save(record);
            if (dev != null && StringUtils.hasText(dev.getBatchId())) {
                productionBatchService.refreshCompletedQtyFromDevices(dev.getBatchId());
            }
        }
        return Result.ok();
    }

    /**
     * 制造商：本企业产生的不合格记录及处置状态。
     */
    @GetMapping("/reject-record/list")
    public Result<PageResult<RejectRecordVO>> listRejectRecords(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        LoginUser user = currentUser();
        return Result.ok(rejectDispositionService.pageForManufacturer(user.getUserId(), pageNum, pageSize));
    }

    /**
     * 制造商：确认销毁类处置已执行完毕并上链。
     */
    @PostMapping("/reject-record/confirm-destroy")
    public Result<Void> confirmRejectDestroy(@RequestBody Map<String, Object> body) {
        Object idObj = body.get("id");
        if (idObj == null) {
            return Result.fail("请提供记录 id");
        }
        long recordId = idObj instanceof Number ? ((Number) idObj).longValue() : Long.parseLong(String.valueOf(idObj));
        rejectDispositionService.confirmDestroyByManufacturer(recordId, currentUser().getUserId());
        return Result.ok();
    }

    private static String normalizeDisposalType(Object raw) {
        if (raw == null) {
            return null;
        }
        String s = String.valueOf(raw).trim().toUpperCase();
        if (s.isEmpty()) {
            return null;
        }
        if ("退货".equals(raw) || "RETURN".equals(s)) {
            return Constants.DISPOSAL_RETURN;
        }
        if ("销毁".equals(raw) || "DESTROY".equals(s)) {
            return Constants.DISPOSAL_DESTROY;
        }
        if (Constants.DISPOSAL_RETURN.equals(s) || Constants.DISPOSAL_DESTROY.equals(s)) {
            return s;
        }
        return null;
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
