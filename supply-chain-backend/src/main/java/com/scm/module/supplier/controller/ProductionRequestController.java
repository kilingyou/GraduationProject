package com.scm.module.supplier.controller;

import com.scm.common.PageResult;
import com.scm.common.Result;
import com.scm.module.supplier.dto.ProductionOrderTrackVO;
import com.scm.module.supplier.dto.ProductionRequestVO;
import com.scm.module.supplier.entity.ProductionRequest;
import com.scm.module.supplier.service.ProductionRequestService;
import com.scm.module.supplier.service.ProductionRequestViewService;
import com.scm.module.supplier.service.SupplierAuditGuardService;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/supplier/order")
@RequiredArgsConstructor
public class ProductionRequestController {

    private final ProductionRequestService productionRequestService;
    private final ProductionRequestViewService productionRequestViewService;
    private final SupplierAuditGuardService supplierAuditGuardService;

    //发起生产订单
    @PostMapping
    public Result<ProductionRequest> create(@RequestBody ProductionRequest request) {
        LoginUser loginUser = getCurrentUser();
        supplierAuditGuardService.ensureApproved(loginUser.getUserId());
        request.setSupplierId(loginUser.getUserId());
        //创建生产订单
        ProductionRequest created = productionRequestService.createOrder(request);
        return Result.ok(created);
    }

    @GetMapping("/list")
    public Result<PageResult<ProductionRequestVO>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String status) {
        LoginUser loginUser = getCurrentUser();
        PageResult<ProductionRequestVO> pageResult =
                productionRequestViewService.pageForSupplier(loginUser.getUserId(), pageNum, pageSize, status);
        return Result.ok(pageResult);
    }

    @GetMapping("/{id}")
    public Result<ProductionRequestVO> detail(@PathVariable Long id) {
        LoginUser loginUser = getCurrentUser();
        ProductionRequestVO vo = productionRequestViewService.detailForSupplier(id, loginUser.getUserId());
        if (vo == null) {
            return Result.fail("订单不存在");
        }
        return Result.ok(vo);
    }

    @GetMapping("/{id}/track")
    public Result<ProductionOrderTrackVO> track(@PathVariable Long id) {
        LoginUser loginUser = getCurrentUser();
        ProductionOrderTrackVO track = productionRequestViewService.trackForSupplier(id, loginUser.getUserId());
        if (track == null) {
            return Result.fail("订单不存在");
        }
        return Result.ok(track);
    }

    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        LoginUser loginUser = getCurrentUser();
        productionRequestService.cancelOrderBySupplier(id, loginUser.getUserId());
        return Result.ok();
    }

    /**
     * 指定组装商领用本单下已放行部件；body 可选 {@code assemblerUserId}，不传或 null 表示清除限制。
     */
    @PostMapping("/{id}/designate-assembler")
    public Result<Void> designateAssembler(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        LoginUser loginUser = getCurrentUser();
        supplierAuditGuardService.ensureApproved(loginUser.getUserId());
        Long assemblerUserId = null;
        if (body != null && body.get("assemblerUserId") != null) {
            Object raw = body.get("assemblerUserId");
            if (raw instanceof Number) {
                assemblerUserId = ((Number) raw).longValue();
            } else {
                String s = String.valueOf(raw).trim();
                if (!s.isEmpty()) {
                    try {
                        assemblerUserId = Long.parseLong(s);
                    } catch (NumberFormatException ignored) {
                        return Result.fail("assemblerUserId 格式无效");
                    }
                }
            }
        }
        productionRequestService.designateAssemblyAssembler(id, loginUser.getUserId(), assemblerUserId);
        return Result.ok();
    }

    private LoginUser getCurrentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
