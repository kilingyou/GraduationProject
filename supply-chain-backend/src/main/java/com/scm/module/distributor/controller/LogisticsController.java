package com.scm.module.distributor.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scm.common.PageResult;
import com.scm.common.Result;
import com.scm.module.distributor.entity.TransferEvent;
import com.scm.module.distributor.service.TransferEventService;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/distributor/logistics")
@RequiredArgsConstructor
public class LogisticsController {

    private final TransferEventService transferEventService;

    @PostMapping("/ship")
    public Result<TransferEvent> ship(@RequestBody TransferEvent transfer) {
        LoginUser loginUser = getCurrentUser();
        transfer.setSenderId(loginUser.getUserId());
        TransferEvent created = transferEventService.createTransfer(transfer);
        return Result.ok(created);
    }

    @PostMapping("/receive")
    public Result<TransferEvent> receive(@RequestBody TransferEvent transfer) {
        LoginUser loginUser = getCurrentUser();
        TransferEvent existing = transferEventService.getById(transfer.getId());
        if (existing == null) {
            return Result.fail("Transfer event not found");
        }
        existing.setActualArrival(LocalDateTime.now());
        existing.setStatus("RECEIVED");
        transferEventService.updateById(existing);
        return Result.ok(existing);
    }

    @GetMapping("/list")
    public Result<PageResult<TransferEvent>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        LoginUser loginUser = getCurrentUser();
        Page<TransferEvent> page = new Page<>(pageNum, pageSize);
        IPage<TransferEvent> sent = transferEventService.listBySender(loginUser.getUserId(), page);

        PageResult<TransferEvent> pageResult = new PageResult<TransferEvent>()
                .setRecords(sent.getRecords())
                .setTotal(sent.getTotal())
                .setCurrent(sent.getCurrent())
                .setSize(sent.getSize());
        return Result.ok(pageResult);
    }

    @GetMapping("/track/{sn}")
    public Result<List<TransferEvent>> track(@PathVariable String sn) {
        List<TransferEvent> events = transferEventService.listBySn(sn);
        return Result.ok(events);
    }

    private LoginUser getCurrentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
