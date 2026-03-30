package com.scm.module.regulator.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scm.module.regulator.entity.RecallNotice;
import com.scm.module.regulator.mapper.RecallNoticeMapper;
import com.scm.module.regulator.service.RecallNoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class RecallNoticeServiceImpl
        extends ServiceImpl<RecallNoticeMapper, RecallNotice>
        implements RecallNoticeService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Random RANDOM = new Random();

    @Override
    public RecallNotice createNotice(RecallNotice notice) {
        if (notice.getNoticeNo() == null || notice.getNoticeNo().isEmpty()) {
            String dateStr = LocalDate.now().format(DATE_FMT);
            String random = String.format("%06d", RANDOM.nextInt(1000000));
            notice.setNoticeNo("RN-" + dateStr + "-" + random);
        }
        if (notice.getStatus() == null) {
            notice.setStatus("PUBLISHED");
        }
        save(notice);
        return notice;
    }

    @Override
    public IPage<RecallNotice> listNotices(Page<RecallNotice> page) {
        return page(page);
    }
}
