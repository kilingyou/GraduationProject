package com.scm.module.manufacturer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scm.module.manufacturer.entity.ProductionBatch;
import com.scm.module.manufacturer.mapper.ProductionBatchMapper;
import com.scm.module.manufacturer.service.ProductionBatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductionBatchServiceImpl
        extends ServiceImpl<ProductionBatchMapper, ProductionBatch>
        implements ProductionBatchService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Override
    public ProductionBatch createBatch(String orderId, Long manufacturerId, Integer qty) {
        String batchId = "BATCH-" + LocalDate.now().format(DATE_FMT) + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        ProductionBatch batch = new ProductionBatch()
                .setBatchId(batchId)
                .setOrderId(orderId)
                .setManufacturerId(manufacturerId)
                .setPlannedQty(qty)
                .setCompletedQty(0)
                .setStatus("CREATED");
        save(batch);
        return batch;
    }

    @Override
    public List<ProductionBatch> listByManufacturer(Long manufacturerId) {
        return list(new LambdaQueryWrapper<ProductionBatch>()
                .eq(ProductionBatch::getManufacturerId, manufacturerId)
                .orderByDesc(ProductionBatch::getCreateTime));
    }
}
