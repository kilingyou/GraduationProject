package com.scm.module.manufacturer.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scm.module.manufacturer.entity.RejectRecord;
import com.scm.module.manufacturer.mapper.RejectRecordMapper;
import com.scm.module.manufacturer.service.RejectRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RejectRecordServiceImpl
        extends ServiceImpl<RejectRecordMapper, RejectRecord>
        implements RejectRecordService {
}
