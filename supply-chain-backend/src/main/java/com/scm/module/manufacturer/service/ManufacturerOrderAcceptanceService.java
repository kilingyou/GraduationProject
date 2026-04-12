package com.scm.module.manufacturer.service;

import com.scm.module.manufacturer.entity.ManufacturingAgreement;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 制造商接单：链上签署与本地订单/协议持久化在同一事务中，避免「库已接单但链失败」的中间态。
 */
public interface ManufacturerOrderAcceptanceService {

    ManufacturingAgreement acceptOrder(
            String orderId,
            Long manufacturerUserId,
            String manufacturerBlockchainAddr,
            BigDecimal finalPrice,
            LocalDate deliveryDate,
            byte[] agreementFileBytes,
            String agreementFilename
    );
}
