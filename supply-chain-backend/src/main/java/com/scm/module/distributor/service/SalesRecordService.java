package com.scm.module.distributor.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.scm.module.distributor.entity.SalesRecord;

public interface SalesRecordService extends IService<SalesRecord> {

    SalesRecord createSale(SalesRecord sale);

    IPage<SalesRecord> listBySeller(Long sellerId, Page<SalesRecord> page);
}
