package com.scm.module.enduser.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.scm.module.enduser.entity.Decommission;

public interface DecommissionService extends IService<Decommission> {

    Decommission createDecommission(Decommission decommission);

    IPage<Decommission> listByApplicant(Long applicantId, Page<Decommission> page);
}
