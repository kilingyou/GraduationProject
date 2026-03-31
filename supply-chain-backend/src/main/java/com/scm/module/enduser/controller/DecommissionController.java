package com.scm.module.enduser.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scm.common.PageResult;
import com.scm.common.Result;
import com.scm.common.exception.BusinessException;
import com.scm.module.enduser.entity.Decommission;
import com.scm.module.enduser.service.DecommissionService;
import com.scm.module.enduser.service.UserProductService;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/enduser/decommission")
@RequiredArgsConstructor
public class DecommissionController {

    private final DecommissionService decommissionService;
    private final UserProductService userProductService;

    @PostMapping
    public Result<Decommission> apply(@RequestBody Decommission decommission) {
        LoginUser loginUser = getCurrentUser();
        decommission.setApplicantId(loginUser.getUserId());
        if (!userProductService.isBound(loginUser.getUserId(), decommission.getSn())) {
            throw new BusinessException("请先完成产品绑定后再申请报废");
        }
        Decommission created = decommissionService.createDecommission(decommission);
        return Result.ok(created);
    }

    @GetMapping("/list")
    public Result<PageResult<Decommission>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        LoginUser loginUser = getCurrentUser();
        int pn = pageNum != null && pageNum > 0 ? pageNum : (page != null && page > 0 ? page : 1);
        int ps = pageSize != null && pageSize > 0 ? pageSize : (size != null && size > 0 ? size : 10);
        Page<Decommission> decPage = new Page<>(pn, ps);
        IPage<Decommission> result = decommissionService.listByApplicant(loginUser.getUserId(), decPage);

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
