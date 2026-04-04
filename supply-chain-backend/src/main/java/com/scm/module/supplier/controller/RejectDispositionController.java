package com.scm.module.supplier.controller;

import com.scm.common.PageResult;
import com.scm.common.Result;
import com.scm.module.manufacturer.dto.RejectRecordVO;
import com.scm.module.manufacturer.service.RejectDispositionService;
import com.scm.module.supplier.service.SupplierAuditGuardService;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 供应商：不合格设备退货处置确认（链上 MFG_REJECT 之后）。
 */
@RestController
@RequestMapping("/api/supplier/reject-disposition")
@RequiredArgsConstructor
public class RejectDispositionController {

    private final RejectDispositionService rejectDispositionService;
    private final SupplierAuditGuardService supplierAuditGuardService;

    @GetMapping("/list")
    public Result<PageResult<RejectRecordVO>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        LoginUser user = currentUser();
        return Result.ok(rejectDispositionService.pageForSupplier(user.getUserId(), pageNum, pageSize));
    }

    @PostMapping("/confirm-return")
    public Result<Void> confirmReturn(@RequestBody Map<String, Object> body) {
        Object idObj = body.get("id");
        if (idObj == null) {
            return Result.fail("请提供记录 id");
        }
        long recordId = idObj instanceof Number ? ((Number) idObj).longValue() : Long.parseLong(String.valueOf(idObj));
        LoginUser user = currentUser();
        supplierAuditGuardService.ensureApproved(user.getUserId());
        rejectDispositionService.confirmReturnBySupplier(recordId, user.getUserId());
        return Result.ok();
    }

    private LoginUser currentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
