package com.scm.module.manufacturer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.scm.common.Constants;
import com.scm.common.exception.BusinessException;
import com.scm.common.util.HashUtil;
import com.scm.integration.blockchain.BlockchainAnchorService;
import com.scm.integration.blockchain.SmartContractInvokeService;
import com.scm.integration.evidence.EvidenceStorageService;
import com.scm.module.manufacturer.entity.ManufacturingAgreement;
import com.scm.module.manufacturer.service.ManufacturerOrderAcceptanceService;
import com.scm.module.manufacturer.service.ManufacturingAgreementService;
import com.scm.module.supplier.entity.ProductionRequest;
import com.scm.module.supplier.service.ProductionRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class ManufacturerOrderAcceptanceServiceImpl implements ManufacturerOrderAcceptanceService {

    private final ProductionRequestService productionRequestService;
    private final ManufacturingAgreementService manufacturingAgreementService;
    private final EvidenceStorageService evidenceStorageService;
    private final BlockchainAnchorService blockchainAnchorService;
    private final SmartContractInvokeService smartContractInvokeService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ManufacturingAgreement acceptOrder(
            String orderId,
            Long manufacturerUserId,
            String manufacturerBlockchainAddr,
            BigDecimal finalPrice,
            LocalDate deliveryDate,
            byte[] agreementFileBytes,
            String agreementFilename) {
        if (!StringUtils.hasText(orderId)) {
            throw new BusinessException("订单号无效");
        }
        if (agreementFileBytes == null || agreementFileBytes.length == 0) {
            throw new BusinessException("请上传已签署的制造协议文件");
        }
        if (finalPrice == null || finalPrice.signum() < 0) {
            throw new BusinessException("最终报价无效");
        }
        if (deliveryDate == null) {
            throw new BusinessException("请填写承诺交期");
        }

        ProductionRequest order = productionRequestService.getOne(
                new LambdaQueryWrapper<ProductionRequest>()
                        .eq(ProductionRequest::getOrderId, orderId.trim()));
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!Constants.PENDING_ACCEPTANCE.equals(order.getStatus())) {
            throw new BusinessException("订单状态不允许接单");
        }
        if (order.getTargetManufacturer() != null
                && !order.getTargetManufacturer().equals(manufacturerUserId)) {
            throw new BusinessException("该订单已定向给其他制造商");
        }

        EvidenceStorageService.StoredEvidence ev = evidenceStorageService.store(
                agreementFileBytes,
                StringUtils.hasText(agreementFilename) ? agreementFilename : "agreement.pdf",
                "MANUFACTURING_AGREEMENT_FILE");

        String fileHashPart = StringUtils.hasText(ev.getFileHash()) ? ev.getFileHash() : "";
        String payload = orderId + "|" + manufacturerUserId + "|" + finalPrice + "|" + deliveryDate + "|" + fileHashPart;

        ManufacturingAgreement agreement = new ManufacturingAgreement();
        agreement.setOrderId(orderId.trim());
        agreement.setManufacturerId(manufacturerUserId);
        agreement.setFinalPrice(finalPrice);
        agreement.setDeliveryDate(deliveryDate);
        agreement.setAgreementHash(ev.getFileHash());
        agreement.setAgreementCid(ev.getIpfsCid());
        if (StringUtils.hasText(manufacturerBlockchainAddr)) {
            agreement.setManufacturerSign("MANUFACTURER_ADDR:" + manufacturerBlockchainAddr.trim());
        }

        productionRequestService.update(new LambdaUpdateWrapper<ProductionRequest>()
                .eq(ProductionRequest::getOrderId, orderId.trim())
                .set(ProductionRequest::getStatus, Constants.ACCEPTED));

        agreement.setTxHash(blockchainAnchorService.anchor(
                "MANUFACTURING_AGREEMENT", HashUtil.sha256Hex(payload)));
        smartContractInvokeService.signManufacturingAgreement(
                orderId.trim(),
                agreement.getAgreementHash(),
                finalPrice.toPlainString(),
                deliveryDate);
        smartContractInvokeService.updateProductionRequestStatus(orderId.trim(), Constants.ACCEPTED);

        manufacturingAgreementService.save(agreement);
        return agreement;
    }
}
