package com.scm.module.distributor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scm.module.distributor.entity.SalesRecord;
import com.scm.module.distributor.mapper.SalesRecordMapper;
import com.scm.module.distributor.service.SalesRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SalesRecordServiceImpl
        extends ServiceImpl<SalesRecordMapper, SalesRecord>
        implements SalesRecordService {

    @Override
    public SalesRecord createSale(SalesRecord sale) {
        if (sale.getSaleTime() == null) {
            sale.setSaleTime(LocalDateTime.now());
        }
        save(sale);
        return sale;
    }

    @Override
    public IPage<SalesRecord> listBySeller(Long sellerId, Page<SalesRecord> page) {
        return page(page, new LambdaQueryWrapper<SalesRecord>()
                .eq(SalesRecord::getSellerId, sellerId)
                .orderByDesc(SalesRecord::getCreateTime));
    }
}
