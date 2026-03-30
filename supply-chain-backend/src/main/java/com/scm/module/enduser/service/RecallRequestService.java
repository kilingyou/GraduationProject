package com.scm.module.enduser.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.scm.module.enduser.entity.RecallRequest;

public interface RecallRequestService extends IService<RecallRequest> {

    RecallRequest createRequest(RecallRequest request);

    IPage<RecallRequest> listByUser(Long userId, Page<RecallRequest> page);
}
