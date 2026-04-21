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

    /**
     * 销售登记接口（支持发票附件上传）。
     * 入参中的 saleTime 需为 ISO-8601 时间字符串，anonymous 支持 true/1。
     *
     * @param sn 产品唯一序列号
     * @param saleTime 销售时间（ISO-8601 字符串，可选）
     * @param customerName 客户姓名（可选）
     * @param customerPhone 客户手机号（可选）
     * @param anonymous 是否匿名销售（true/1 表示匿名）
     * @param customerSegment 客户分层标签（可选）
     * @param invoice 发票附件（可选）
     * @return 销售登记结果
     * @throws IOException 文件处理异常
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<SalesRecord> registerSale(
            @RequestParam String sn,
            @RequestParam(required = false) String saleTime,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String customerPhone,
            @RequestParam(required = false) String anonymous,
            @RequestParam(required = false) String customerSegment,
            @RequestPart(value = "invoice", required = false) MultipartFile invoice) throws IOException {
        // 获取当前登录用户，作为销售登记的操作人
        LoginUser loginUser = getCurrentUser();
        LocalDateTime st = null;
        // 解析可选销售时间（ISO-8601）并转换为系统时区时间
        if (saleTime != null && !saleTime.trim().isEmpty()) {
            st = LocalDateTime.ofInstant(Instant.parse(saleTime), ZoneId.systemDefault());
        }
        // 匿名标记兼容 true/1 两种传参格式
        boolean anon = "true".equalsIgnoreCase(anonymous) || "1".equals(anonymous);
        // 调用服务层完成销售登记（含可选发票文件处理）
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
