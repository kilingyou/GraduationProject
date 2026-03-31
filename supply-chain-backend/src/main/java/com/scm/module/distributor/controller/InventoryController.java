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
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/distributor/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final AssemblyRecordService assemblyRecordService;

    /**
     * 我的库存：按 current_holder_id = 当前分销商，可筛选在库/在途（不含已售出）。
     * 升级库需执行 db/alter_assembly_current_holder.sql。
     */
    @GetMapping("/list")
    public Result<PageResult<AssemblyRecord>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sn,
            @RequestParam(required = false) String status) {
        LoginUser loginUser = getCurrentUser();
        int pn = pageNum != null && pageNum > 0 ? pageNum : (page != null && page > 0 ? page : 1);
        int ps = pageSize != null && pageSize > 0 ? pageSize : (size != null && size > 0 ? size : 10);
        Page<AssemblyRecord> p = new Page<>(pn, ps);

        LambdaQueryWrapper<AssemblyRecord> w = new LambdaQueryWrapper<AssemblyRecord>()
                .eq(AssemblyRecord::getCurrentHolderId, loginUser.getUserId())
                .ne(AssemblyRecord::getStatus, "SOLD")
                .orderByDesc(AssemblyRecord::getUpdateTime);
        if (StringUtils.hasText(sn)) {
            w.like(AssemblyRecord::getSn, sn.trim());
        }
        if (StringUtils.hasText(status)) {
            w.eq(AssemblyRecord::getStatus, status.trim());
        }

        IPage<AssemblyRecord> result = assemblyRecordService.page(p, w);
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
