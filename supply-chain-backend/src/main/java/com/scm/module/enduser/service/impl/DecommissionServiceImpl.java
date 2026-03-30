package com.scm.module.enduser.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scm.module.enduser.entity.Decommission;
import com.scm.module.enduser.mapper.DecommissionMapper;
import com.scm.module.enduser.service.DecommissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DecommissionServiceImpl
        extends ServiceImpl<DecommissionMapper, Decommission>
        implements DecommissionService {

    @Override
    public Decommission createDecommission(Decommission decommission) {
        if (decommission.getStatus() == null) {
            decommission.setStatus("APPLIED");
        }
        save(decommission);
        return decommission;
    }

    @Override
    public IPage<Decommission> listByApplicant(Long applicantId, Page<Decommission> page) {
        return page(page, new LambdaQueryWrapper<Decommission>()
                .eq(Decommission::getApplicantId, applicantId)
                .orderByDesc(Decommission::getCreateTime));
    }
}
