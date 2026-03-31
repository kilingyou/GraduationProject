package com.scm.module.regulator.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scm.common.Result;
import com.scm.common.util.HashUtil;
import com.scm.integration.blockchain.BlockchainAnchorService;
import com.scm.module.system.entity.SysSupplierAudit;
import com.scm.module.system.mapper.SysSupplierAuditMapper;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/regulator/audit")
@RequiredArgsConstructor
public class AuditController {

    private final SysSupplierAuditMapper sysSupplierAuditMapper;
    private final BlockchainAnchorService blockchainAnchorService;

    @GetMapping("/list")
    public Result<List<SysSupplierAudit>> list() {
        List<SysSupplierAudit> audits = sysSupplierAuditMapper.selectList(
                new LambdaQueryWrapper<SysSupplierAudit>()
                        .eq(SysSupplierAudit::getAuditStatus, "PENDING")
                        .orderByDesc(SysSupplierAudit::getCreateTime));
        return Result.ok(audits);
    }

    @PostMapping("/{id}/approve")
    public Result<SysSupplierAudit> approve(@PathVariable Long id) {
        LoginUser loginUser = getCurrentUser();
        SysSupplierAudit audit = sysSupplierAuditMapper.selectById(id);
        if (audit == null) {
            return Result.fail("Audit record not found");
        }
        audit.setAuditStatus("APPROVED");
        audit.setAuditorId(loginUser.getUserId());
        audit.setAuditTime(LocalDateTime.now());
        String apPayload = audit.getId() + "|" + audit.getUserId() + "|" + audit.getEnterpriseName();
        audit.setTxHash(blockchainAnchorService.anchor("SUPPLIER_APPROVE", HashUtil.sha256Hex(apPayload)));
        sysSupplierAuditMapper.updateById(audit);
        return Result.ok(audit);
    }

    @PostMapping("/{id}/reject")
    public Result<SysSupplierAudit> reject(@PathVariable Long id,
                                           @RequestBody(required = false) SysSupplierAudit body) {
        LoginUser loginUser = getCurrentUser();
        SysSupplierAudit audit = sysSupplierAuditMapper.selectById(id);
        if (audit == null) {
            return Result.fail("Audit record not found");
        }
        audit.setAuditStatus("REJECTED");
        audit.setAuditorId(loginUser.getUserId());
        audit.setAuditTime(LocalDateTime.now());
        if (body != null && body.getAuditOpinion() != null) {
            audit.setAuditOpinion(body.getAuditOpinion());
        }
        String rjPayload = audit.getId() + "|REJECT|" + audit.getAuditOpinion();
        audit.setTxHash(blockchainAnchorService.anchor("SUPPLIER_REJECT", HashUtil.sha256Hex(rjPayload)));
        sysSupplierAuditMapper.updateById(audit);
        return Result.ok(audit);
    }

    private LoginUser getCurrentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
