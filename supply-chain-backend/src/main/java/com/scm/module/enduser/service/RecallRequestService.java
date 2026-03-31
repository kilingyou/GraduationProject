package com.scm.module.enduser.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.scm.module.enduser.entity.RecallRequest;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface RecallRequestService extends IService<RecallRequest> {

    RecallRequest createRequest(RecallRequest request);

    RecallRequest createRequest(RecallRequest request, List<MultipartFile> evidenceFiles) throws IOException;

    IPage<RecallRequest> listByUser(Long userId, Page<RecallRequest> page);
}
