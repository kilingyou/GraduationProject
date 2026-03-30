package com.scm.module.enduser.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scm.module.enduser.entity.RecallRequest;
import com.scm.module.enduser.mapper.RecallRequestMapper;
import com.scm.module.enduser.service.RecallRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class RecallRequestServiceImpl
        extends ServiceImpl<RecallRequestMapper, RecallRequest>
        implements RecallRequestService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Random RANDOM = new Random();

    @Override
    public RecallRequest createRequest(RecallRequest request) {
        if (request.getRequestNo() == null || request.getRequestNo().isEmpty()) {
            String dateStr = LocalDate.now().format(DATE_FMT);
            String random = String.format("%06d", RANDOM.nextInt(1000000));
            request.setRequestNo("RR-" + dateStr + "-" + random);
        }
        if (request.getStatus() == null) {
            request.setStatus("SUBMITTED");
        }
        save(request);
        return request;
    }

    @Override
    public IPage<RecallRequest> listByUser(Long userId, Page<RecallRequest> page) {
        return page(page, new LambdaQueryWrapper<RecallRequest>()
                .eq(RecallRequest::getUserId, userId)
                .orderByDesc(RecallRequest::getCreateTime));
    }
}
