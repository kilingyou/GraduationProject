package com.scm.module.enduser.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scm.common.PageResult;
import com.scm.common.Result;
import com.scm.module.enduser.entity.RecallRequest;
import com.scm.module.enduser.service.RecallRequestService;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/enduser/complaint")
@RequiredArgsConstructor
public class ComplaintController {

    private final RecallRequestService recallRequestService;

    @PostMapping
    public Result<RecallRequest> submit(@RequestBody RecallRequest request) {
        LoginUser loginUser = getCurrentUser();
        request.setUserId(loginUser.getUserId());
        RecallRequest created = recallRequestService.createRequest(request);
        return Result.ok(created);
    }

    @GetMapping("/list")
    public Result<PageResult<RecallRequest>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        LoginUser loginUser = getCurrentUser();
        Page<RecallRequest> page = new Page<>(pageNum, pageSize);
        IPage<RecallRequest> result = recallRequestService.listByUser(loginUser.getUserId(), page);

        PageResult<RecallRequest> pageResult = new PageResult<RecallRequest>()
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
