package com.scm.module.assembler.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scm.common.PageResult;
import com.scm.common.Result;
import com.scm.integration.evidence.EvidenceStorageService;
import com.scm.module.assembler.dto.AssemblyQcBatchOutcome;
import com.scm.module.assembler.entity.AssemblyRecord;
import com.scm.module.assembler.service.AssemblyRecordService;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/assembler/quality")
@RequiredArgsConstructor
public class AssemblerQualityController {

    private final AssemblyRecordService assemblyRecordService;
    private final EvidenceStorageService evidenceStorageService;

    @PostMapping(value = "/report", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<?> uploadReport(
            @RequestPart("file") MultipartFile file,
            @RequestParam String targetType,
            @RequestParam String targetId,
            @RequestParam String result) throws IOException {
        LoginUser loginUser = getCurrentUser();
        if (file == null || file.isEmpty()) {
            return Result.fail("请上传报告文件");
        }
        EvidenceStorageService.StoredEvidence ev = evidenceStorageService.store(
                file.getBytes(), file.getOriginalFilename(), "ASSEMBLY_QC_REPORT");

        if ("SN".equalsIgnoreCase(targetType)) {
            AssemblyRecord record = assemblyRecordService.listBySn(targetId);
            if (record == null) {
                return Result.fail("未找到 SN 对应的组装记录: " + targetId);
            }
            if (!loginUser.getUserId().equals(record.getAssemblerId())) {
                return Result.fail("无权操作该记录");
            }
            applyReport(record, ev, result);
            assemblyRecordService.updateById(record);
            return Result.ok(record);
        }
        if ("BATCH".equalsIgnoreCase(targetType)) {
            if (!StringUtils.hasText(targetId)) {
                return Result.fail("请输入批次号");
            }
            String batchNo = targetId.trim();
            List<AssemblyRecord> records = assemblyRecordService.list(
                    new LambdaQueryWrapper<AssemblyRecord>()
                            .eq(AssemblyRecord::getAssemblyBatchNo, batchNo)
                            .eq(AssemblyRecord::getAssemblerId, loginUser.getUserId()));
            if (records.isEmpty()) {
                return Result.fail("批次下无组装记录或无权操作");
            }
            for (AssemblyRecord r : records) {
                applyReport(r, ev, result);
            }
            assemblyRecordService.updateBatchById(records);
            AssemblyQcBatchOutcome out = new AssemblyQcBatchOutcome().setUpdated(records.size());
            return Result.ok("已对该批次 " + records.size() + " 台整机写入同一质检结果", out);
        }
        if ("MULTI_SN".equalsIgnoreCase(targetType)) {
            AssemblyQcBatchOutcome outcome = applyReportToSnList(loginUser.getUserId(), targetId, ev, result);
            if (outcome.getUpdated() == 0) {
                return Result.fail(outcome.getFailed().isEmpty()
                        ? "未解析到有效 SN"
                        : String.join("；", outcome.getFailed()));
            }
            String msg = "已更新 " + outcome.getUpdated() + " 台整机"
                    + (outcome.getFailed().isEmpty() ? "" : "，部分失败见返回详情");
            return Result.ok(msg, outcome);
        }
        return Result.fail("不支持的 targetType: " + targetType);
    }

    private AssemblyQcBatchOutcome applyReportToSnList(Long assemblerId, String rawSnList,
                                                        EvidenceStorageService.StoredEvidence ev, String result) {
        AssemblyQcBatchOutcome outcome = new AssemblyQcBatchOutcome();
        Set<String> sns = parseSnTokens(rawSnList);
        if (sns.isEmpty()) {
            return outcome;
        }
        List<AssemblyRecord> toUpdate = new ArrayList<>();
        for (String sn : sns) {
            AssemblyRecord record = assemblyRecordService.listBySn(sn);
            if (record == null) {
                outcome.getFailed().add(sn + ": 未找到组装记录");
                continue;
            }
            if (!assemblerId.equals(record.getAssemblerId())) {
                outcome.getFailed().add(sn + ": 无权操作");
                continue;
            }
            applyReport(record, ev, result);
            toUpdate.add(record);
        }
        if (!toUpdate.isEmpty()) {
            assemblyRecordService.updateBatchById(toUpdate);
            outcome.setUpdated(toUpdate.size());
        }
        return outcome;
    }

    private static Set<String> parseSnTokens(String raw) {
        Set<String> out = new LinkedHashSet<>();
        if (!StringUtils.hasText(raw)) {
            return out;
        }
        for (String line : raw.split("\\R")) {
            if (!StringUtils.hasText(line)) {
                continue;
            }
            for (String part : line.split("[,;，；、\\s]+")) {
                if (StringUtils.hasText(part)) {
                    out.add(part.trim());
                }
            }
        }
        return out;
    }

    private static void applyReport(AssemblyRecord r, EvidenceStorageService.StoredEvidence ev, String result) {
        r.setTestReportHash(ev.getFileHash());
        r.setTestReportCid(ev.getIpfsCid());
        r.setTestResult(result);
        if (r.getTxHash() == null || r.getTxHash().trim().isEmpty()) {
            r.setTxHash(ev.getTxHash());
        }
    }

    @GetMapping("/report/list")
    public Result<PageResult<AssemblyRecord>> listReports(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        LoginUser loginUser = getCurrentUser();
        int pn = page != null ? page : pageNum;
        int ps = size != null ? size : pageSize;
        Page<AssemblyRecord> p = new Page<>(pn, ps);

        IPage<AssemblyRecord> result = assemblyRecordService.page(p,
                new LambdaQueryWrapper<AssemblyRecord>()
                        .eq(AssemblyRecord::getAssemblerId, loginUser.getUserId())
                        .isNotNull(AssemblyRecord::getTestReportHash)
                        .orderByDesc(AssemblyRecord::getCreateTime));

        PageResult<AssemblyRecord> pageResult = new PageResult<AssemblyRecord>()
                .setRecords(result.getRecords())
                .setTotal(result.getTotal())
                .setCurrent(result.getCurrent())
                .setSize(result.getSize());
        return Result.ok(pageResult);
    }

    private LoginUser getCurrentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
