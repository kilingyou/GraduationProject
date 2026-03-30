package com.scm.module.supplier.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scm.module.supplier.entity.ProductionRequest;
import com.scm.module.supplier.mapper.ProductionRequestMapper;
import com.scm.module.supplier.service.ProductionRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionRequestServiceImpl extends ServiceImpl<ProductionRequestMapper, ProductionRequest>
        implements ProductionRequestService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductionRequest createOrder(ProductionRequest request) {
        request.setOrderId(UUID.randomUUID().toString().replace("-", ""));
        request.setStatus("PENDING_ACCEPTANCE");
        save(request);

        // Stub: submit to blockchain
        log.info("Production order created: id={}, orderId={}", request.getId(), request.getOrderId());
        return request;
    }

    @Override
    public IPage<ProductionRequest> listBySupplier(Long supplierId, Page<ProductionRequest> page, String status) {
        LambdaQueryWrapper<ProductionRequest> wrapper = new LambdaQueryWrapper<ProductionRequest>()
                .eq(ProductionRequest::getSupplierId, supplierId)
                .eq(StringUtils.hasText(status), ProductionRequest::getStatus, status)
                .orderByDesc(ProductionRequest::getCreateTime);
        return page(page, wrapper);
    }

    @Override
    public IPage<ProductionRequest> listForManufacturer(Long manufacturerId, Page<ProductionRequest> page) {
        LambdaQueryWrapper<ProductionRequest> wrapper = new LambdaQueryWrapper<ProductionRequest>()
                .and(w -> w.eq(ProductionRequest::getTargetManufacturer, manufacturerId)
                        .or()
                        .isNull(ProductionRequest::getTargetManufacturer))
                .orderByDesc(ProductionRequest::getCreateTime);
        return page(page, wrapper);
    }
}
