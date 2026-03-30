package com.scm.module.supplier.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scm.common.PageResult;
import com.scm.common.Result;
import com.scm.module.supplier.entity.ProductionRequest;
import com.scm.module.supplier.service.ProductionRequestService;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/supplier/order")
@RequiredArgsConstructor
public class ProductionRequestController {

    private final ProductionRequestService productionRequestService;

    @PostMapping
    public Result<ProductionRequest> create(@RequestBody ProductionRequest request) {
        LoginUser loginUser = getCurrentUser();
        request.setSupplierId(loginUser.getUserId());

        ProductionRequest created = productionRequestService.createOrder(request);
        return Result.ok(created);
    }

    @GetMapping("/list")
    public Result<PageResult<ProductionRequest>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String status) {
        LoginUser loginUser = getCurrentUser();

        Page<ProductionRequest> page = new Page<>(pageNum, pageSize);
        IPage<ProductionRequest> result = productionRequestService.listBySupplier(
                loginUser.getUserId(), page, status);

        PageResult<ProductionRequest> pageResult = new PageResult<ProductionRequest>()
                .setRecords(result.getRecords())
                .setTotal(result.getTotal())
                .setCurrent(result.getCurrent())
                .setSize(result.getSize());
        return Result.ok(pageResult);
    }

    @GetMapping("/{id}")
    public Result<ProductionRequest> detail(@PathVariable Long id) {
        ProductionRequest request = productionRequestService.getById(id);
        if (request == null) {
            return Result.fail("Production request not found");
        }
        return Result.ok(request);
    }

    private LoginUser getCurrentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
