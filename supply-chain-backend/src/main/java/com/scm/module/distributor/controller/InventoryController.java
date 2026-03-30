package com.scm.module.distributor.controller;

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

@RestController
@RequestMapping("/api/distributor/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final AssemblyRecordService assemblyRecordService;

    @GetMapping("/list")
    public Result<PageResult<AssemblyRecord>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        LoginUser loginUser = getCurrentUser();
        Page<AssemblyRecord> page = new Page<>(pageNum, pageSize);

        IPage<AssemblyRecord> result = assemblyRecordService.page(page,
                new LambdaQueryWrapper<AssemblyRecord>()
                        .eq(AssemblyRecord::getStatus, "IN_STOCK")
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
