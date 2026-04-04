package com.scm.module.distributor.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.scm.module.distributor.entity.SalesRecord;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;

public interface SalesRecordService extends IService<SalesRecord> {

    SalesRecord registerSale(String sn, LocalDateTime saleTime, String customerName, String customerPhone,
                             MultipartFile invoice, Long sellerId, boolean anonymous, String customerSegment)
            throws IOException;

    IPage<SalesRecord> listBySeller(Long sellerId, Page<SalesRecord> page);

    SalesRecord getLatestBySn(String sn);
}
