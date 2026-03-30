package com.scm.module.assembler.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scm.common.PageResult;
import com.scm.common.Result;
import com.scm.module.assembler.entity.AssemblyBatch;
import com.scm.module.assembler.entity.AssemblyRecord;
import com.scm.module.assembler.service.AssemblyBatchService;
import com.scm.module.assembler.service.AssemblyRecordService;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assembler/assembly")
@RequiredArgsConstructor
public class AssemblyController {

    private final AssemblyBatchService assemblyBatchService;
    private final AssemblyRecordService assemblyRecordService;

    @PostMapping("/batch")
    public Result<AssemblyBatch> createBatch(@RequestBody AssemblyBatch batch) {
        LoginUser loginUser = getCurrentUser();
        batch.setAssemblerId(loginUser.getUserId());
        AssemblyBatch created = assemblyBatchService.createBatch(batch);
        return Result.ok(created);
    }

    @GetMapping("/batch/list")
    public Result<PageResult<AssemblyBatch>> listBatches(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        LoginUser loginUser = getCurrentUser();
        Page<AssemblyBatch> page = new Page<>(pageNum, pageSize);
        IPage<AssemblyBatch> result = assemblyBatchService.listByAssembler(loginUser.getUserId(), page);

        PageResult<AssemblyBatch> pageResult = new PageResult<AssemblyBatch>()
                .setRecords(result.getRecords())
                .setTotal(result.getTotal())
                .setCurrent(result.getCurrent())
                .setSize(result.getSize());
        return Result.ok(pageResult);
    }

    @PostMapping("/record")
    public Result<AssemblyRecord> createRecord(@RequestBody AssemblyRecord record) {
        LoginUser loginUser = getCurrentUser();
        record.setAssemblerId(loginUser.getUserId());
        AssemblyRecord created = assemblyRecordService.createRecord(record);
        return Result.ok(created);
    }

    @GetMapping("/record/list")
    public Result<PageResult<AssemblyRecord>> listRecords(
            @RequestParam String batchNo,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<AssemblyRecord> page = new Page<>(pageNum, pageSize);
        IPage<AssemblyRecord> result = assemblyRecordService.listByBatch(batchNo, page);

        PageResult<AssemblyRecord> pageResult = new PageResult<AssemblyRecord>()
                .setRecords(result.getRecords())
                .setTotal(result.getTotal())
                .setCurrent(result.getCurrent())
                .setSize(result.getSize());
        return Result.ok(pageResult);
    }

    @PostMapping("/record/{id}/register")
    public Result<Boolean> registerOnChain(@PathVariable Long id, @RequestBody List<Long> ids) {
        boolean success = assemblyRecordService.registerOnChain(ids);
        return success ? Result.ok(true) : Result.fail("Register on chain failed");
    }

    private LoginUser getCurrentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
