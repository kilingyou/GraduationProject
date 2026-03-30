package com.scm.module.assembler.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scm.common.PageResult;
import com.scm.common.Result;
import com.scm.module.assembler.entity.AssemblyRecord;
import com.scm.module.assembler.service.AssemblyRecordService;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/assembler/quality")
@RequiredArgsConstructor
public class AssemblerQualityController {

    private final AssemblyRecordService assemblyRecordService;

    @PostMapping("/report")
    public Result<AssemblyRecord> uploadReport(@RequestBody Map<String, String> body) {
        LoginUser loginUser = getCurrentUser();
        String sn = body.get("sn");
        String testReportHash = body.get("testReportHash");
        String testReportCid = body.get("testReportCid");
        String testResult = body.get("testResult");

        if (sn == null || sn.isEmpty()) {
            return Result.fail("SN is required");
        }

        AssemblyRecord record = assemblyRecordService.listBySn(sn);
        if (record == null) {
            return Result.fail("Assembly record not found for SN: " + sn);
        }

        record.setTestReportHash(testReportHash);
        record.setTestReportCid(testReportCid);
        record.setTestResult(testResult);
        assemblyRecordService.updateById(record);
        return Result.ok(record);
    }

    @GetMapping("/report/list")
    public Result<PageResult<AssemblyRecord>> listReports(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        LoginUser loginUser = getCurrentUser();
        Page<AssemblyRecord> page = new Page<>(pageNum, pageSize);

        IPage<AssemblyRecord> result = assemblyRecordService.page(page,
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
