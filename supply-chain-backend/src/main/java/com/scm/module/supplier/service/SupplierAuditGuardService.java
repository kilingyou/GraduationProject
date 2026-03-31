package com.scm.module.supplier.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scm.common.exception.BusinessException;
import com.scm.module.system.entity.SysSupplierAudit;
import com.scm.module.system.mapper.SysSupplierAuditMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SupplierAuditGuardService {

    private final SysSupplierAuditMapper sysSupplierAuditMapper;

    public void ensureApproved(Long supplierUserId) {
        SysSupplierAudit audit = sysSupplierAuditMapper.selectOne(
                new LambdaQueryWrapper<SysSupplierAudit>()
                        .eq(SysSupplierAudit::getUserId, supplierUserId)
                        .orderByDesc(SysSupplierAudit::getCreateTime)
                        .last("LIMIT 1")
        );

        if (audit == null || !"APPROVED".equalsIgnoreCase(audit.getAuditStatus())) {
            throw new BusinessException("供应商资质待审核，审核通过后才可执行该操作");
        }
    }
}
