package com.scm.module.regulator.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scm.common.PageResult;
import com.scm.common.Result;
import com.scm.module.assembler.entity.AssemblyRecord;
import com.scm.module.assembler.service.AssemblyRecordService;
import com.scm.module.regulator.entity.RecallNotice;
import com.scm.module.regulator.service.RecallNoticeService;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/regulator/recall")
@RequiredArgsConstructor
public class RecallController {

    private final RecallNoticeService recallNoticeService;
    private final AssemblyRecordService assemblyRecordService;

    @PostMapping
    public Result<RecallNotice> create(@RequestBody RecallNotice notice) {
        LoginUser loginUser = getCurrentUser();
        notice.setIssuerId(loginUser.getUserId());
        RecallNotice created = recallNoticeService.createNotice(notice);
        return Result.ok(created);
    }

    @GetMapping("/list")
    public Result<PageResult<RecallNotice>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<RecallNotice> page = new Page<>(pageNum, pageSize);
        IPage<RecallNotice> result = recallNoticeService.listNotices(page);

        PageResult<RecallNotice> pageResult = new PageResult<RecallNotice>()
                .setRecords(result.getRecords())
                .setTotal(result.getTotal())
                .setCurrent(result.getCurrent())
                .setSize(result.getSize());
        return Result.ok(pageResult);
    }

    @GetMapping("/analyze/{sn}")
    public Result<Map<String, Object>> analyze(@PathVariable String sn) {
        Map<String, Object> analysis = new HashMap<>();
        analysis.put("sn", sn);

        AssemblyRecord record = assemblyRecordService.listBySn(sn);
        if (record != null) {
            analysis.put("assemblyRecord", record);
            analysis.put("ecidList", record.getEcidList());
            analysis.put("batchNo", record.getAssemblyBatchNo());
            analysis.put("status", record.getStatus());
        } else {
            analysis.put("message", "No assembly record found for SN: " + sn);
        }

        return Result.ok(analysis);
    }

    private LoginUser getCurrentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
