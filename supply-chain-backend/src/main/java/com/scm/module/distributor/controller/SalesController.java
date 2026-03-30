package com.scm.module.distributor.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scm.common.PageResult;
import com.scm.common.Result;
import com.scm.module.distributor.entity.SalesRecord;
import com.scm.module.distributor.service.SalesRecordService;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/distributor/sales")
@RequiredArgsConstructor
public class SalesController {

    private final SalesRecordService salesRecordService;

    @PostMapping
    public Result<SalesRecord> registerSale(@RequestBody SalesRecord sale) {
        LoginUser loginUser = getCurrentUser();
        sale.setSellerId(loginUser.getUserId());
        SalesRecord created = salesRecordService.createSale(sale);
        return Result.ok(created);
    }

    @GetMapping("/list")
    public Result<PageResult<SalesRecord>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        LoginUser loginUser = getCurrentUser();
        Page<SalesRecord> page = new Page<>(pageNum, pageSize);
        IPage<SalesRecord> result = salesRecordService.listBySeller(loginUser.getUserId(), page);

        PageResult<SalesRecord> pageResult = new PageResult<SalesRecord>()
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
