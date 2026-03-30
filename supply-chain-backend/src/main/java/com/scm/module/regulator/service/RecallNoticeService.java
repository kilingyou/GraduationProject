package com.scm.module.regulator.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.scm.module.regulator.entity.RecallNotice;

public interface RecallNoticeService extends IService<RecallNotice> {

    RecallNotice createNotice(RecallNotice notice);

    IPage<RecallNotice> listNotices(Page<RecallNotice> page);
}
