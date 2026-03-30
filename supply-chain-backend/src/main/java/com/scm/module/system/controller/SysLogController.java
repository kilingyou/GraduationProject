package com.scm.module.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scm.common.PageResult;
import com.scm.common.Result;
import com.scm.module.system.entity.SysOperateLog;
import com.scm.module.system.mapper.SysOperateLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/log")
@RequiredArgsConstructor
public class SysLogController {

    private final SysOperateLogMapper sysOperateLogMapper;

    @GetMapping("/list")
    public Result<PageResult<SysOperateLog>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String operation) {

        LambdaQueryWrapper<SysOperateLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(username), SysOperateLog::getUsername, username);
        wrapper.like(StringUtils.hasText(operation), SysOperateLog::getOperation, operation);
        wrapper.orderByDesc(SysOperateLog::getOperationTime);

        IPage<SysOperateLog> result = sysOperateLogMapper.selectPage(new Page<>(page, size), wrapper);
        PageResult<SysOperateLog> pageResult = new PageResult<SysOperateLog>()
                .setRecords(result.getRecords())
                .setTotal(result.getTotal())
                .setCurrent(result.getCurrent())
                .setSize(result.getSize());
        return Result.ok(pageResult);
    }
}
