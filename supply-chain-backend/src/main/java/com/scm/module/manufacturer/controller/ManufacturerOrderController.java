package com.scm.module.manufacturer.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.scm.common.Result;
import com.scm.module.manufacturer.entity.ManufacturingAgreement;
import com.scm.module.manufacturer.service.ManufacturingAgreementService;
import com.scm.module.supplier.entity.ProductionRequest;
import com.scm.module.supplier.service.ProductionRequestService;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/manufacturer/order")
@RequiredArgsConstructor
public class ManufacturerOrderController {

    private final ProductionRequestService productionRequestService;
    private final ManufacturingAgreementService agreementService;

    @GetMapping("/list")
    public Result<List<ProductionRequest>> listAvailableOrders() {
        LoginUser user = currentUser();
        List<ProductionRequest> orders = productionRequestService.list(
                new LambdaQueryWrapper<ProductionRequest>()
                        .eq(ProductionRequest::getStatus, "PENDING_ACCEPTANCE")
                        .and(w -> w.isNull(ProductionRequest::getTargetManufacturer)
                                .or()
                                .eq(ProductionRequest::getTargetManufacturer, user.getUserId()))
                        .orderByDesc(ProductionRequest::getCreateTime));
        return Result.ok(orders);
    }

    @PostMapping("/{orderId}/accept")
    public Result<ManufacturingAgreement> acceptOrder(@PathVariable String orderId,
                                                      @RequestBody ManufacturingAgreement agreement) {
        LoginUser user = currentUser();

        ProductionRequest order = productionRequestService.getOne(
                new LambdaQueryWrapper<ProductionRequest>()
                        .eq(ProductionRequest::getOrderId, orderId));
        if (order == null) {
            return Result.fail("订单不存在");
        }
        if (!"PENDING_ACCEPTANCE".equals(order.getStatus())) {
            return Result.fail("订单状态不允许接受");
        }

        productionRequestService.update(new LambdaUpdateWrapper<ProductionRequest>()
                .eq(ProductionRequest::getOrderId, orderId)
                .set(ProductionRequest::getStatus, "ACCEPTED"));

        agreement.setOrderId(orderId);
        agreement.setManufacturerId(user.getUserId());
        agreementService.signAgreement(agreement);

        return Result.ok(agreement);
    }

    @GetMapping("/{orderId}/agreement")
    public Result<ManufacturingAgreement> viewAgreement(@PathVariable String orderId) {
        ManufacturingAgreement agreement = agreementService.getOne(
                new LambdaQueryWrapper<ManufacturingAgreement>()
                        .eq(ManufacturingAgreement::getOrderId, orderId));
        if (agreement == null) {
            return Result.fail("协议不存在");
        }
        return Result.ok(agreement);
    }

    private LoginUser currentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
