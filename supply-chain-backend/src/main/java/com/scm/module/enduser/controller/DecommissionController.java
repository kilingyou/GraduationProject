package com.scm.module.enduser.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scm.common.PageResult;
import com.scm.common.Result;
import com.scm.module.enduser.entity.Decommission;
import com.scm.module.enduser.service.DecommissionService;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/enduser/decommission")
@RequiredArgsConstructor
public class DecommissionController {

    private final DecommissionService decommissionService;

    @PostMapping
    public Result<Decommission> apply(@RequestBody Decommission decommission) {
        LoginUser loginUser = getCurrentUser();
        decommission.setApplicantId(loginUser.getUserId());
        Decommission created = decommissionService.createDecommission(decommission);
        return Result.ok(created);
    }

    @GetMapping("/list")
    public Result<PageResult<Decommission>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        LoginUser loginUser = getCurrentUser();
        Page<Decommission> page = new Page<>(pageNum, pageSize);
        IPage<Decommission> result = decommissionService.listByApplicant(loginUser.getUserId(), page);

        PageResult<Decommission> pageResult = new PageResult<Decommission>()
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
