package com.scm.module.distributor.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scm.common.PageResult;
import com.scm.common.Result;
import com.scm.module.distributor.entity.SalesRecord;
import com.scm.module.distributor.service.SalesRecordService;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/distributor/sales")
@RequiredArgsConstructor
public class SalesController {

    private final SalesRecordService salesRecordService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<SalesRecord> registerSale(
            @RequestParam String sn,
            @RequestParam(required = false) String saleTime,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String customerPhone,
            @RequestParam(required = false) String anonymous,
            @RequestParam(required = false) String customerSegment,
            @RequestPart(value = "invoice", required = false) MultipartFile invoice) throws IOException {
        LoginUser loginUser = getCurrentUser();
        LocalDateTime st = null;
        if (saleTime != null && !saleTime.trim().isEmpty()) {
            st = LocalDateTime.ofInstant(Instant.parse(saleTime), ZoneId.systemDefault());
        }
        boolean anon = "true".equalsIgnoreCase(anonymous) || "1".equals(anonymous);
        SalesRecord created = salesRecordService.registerSale(sn, st, customerName, customerPhone, invoice,
                loginUser.getUserId(), anon, customerSegment);
        return Result.ok(created);
    }

    @GetMapping("/list")
    public Result<PageResult<SalesRecord>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        LoginUser loginUser = getCurrentUser();
        int pn = page != null ? page : pageNum;
        int ps = size != null ? size : pageSize;
        Page<SalesRecord> p = new Page<>(pn, ps);
        IPage<SalesRecord> result = salesRecordService.listBySeller(loginUser.getUserId(), p);

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
