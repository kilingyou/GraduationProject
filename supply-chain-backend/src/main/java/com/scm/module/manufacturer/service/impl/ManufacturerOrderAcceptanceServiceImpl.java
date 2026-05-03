package com.scm.module.manufacturer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.scm.common.Constants;
import com.scm.common.exception.BusinessException;
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
        // 1) 入参基础校验：订单号必须存在，避免后续查询出现无效条件
        if (!StringUtils.hasText(orderId)) {
            throw new BusinessException("订单号无效");
        }
        // 2) 必须上传已签署的制造协议文件（接单行为需要有签署凭证）
        if (agreementFileBytes == null || agreementFileBytes.length == 0) {
            throw new BusinessException("请上传已签署的制造协议文件");
        }
        // 3) 最终报价校验：不能为空且不能为负数
        if (finalPrice == null || finalPrice.signum() < 0) {
            throw new BusinessException("最终报价无效");
        }
        // 4) 承诺交期不能为空
        if (deliveryDate == null) {
            throw new BusinessException("请填写承诺交期");
        }

        // 5) 根据业务订单号查询生产订单
        ProductionRequest order = productionRequestService.getOne(
                new LambdaQueryWrapper<ProductionRequest>()
                        .eq(ProductionRequest::getOrderId, orderId.trim()));
        // 6) 订单不存在则无法接单
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        // 7) 仅允许“待接单”状态执行接单，防止重复接单或越状态操作
        if (!Constants.PENDING_ACCEPTANCE.equals(order.getStatus())) {
            throw new BusinessException("订单状态不允许接单");
        }
        // 8) 若订单为定向订单，则仅被指定制造商可接单
        if (order.getTargetManufacturer() != null
                && !order.getTargetManufacturer().equals(manufacturerUserId)) {
            throw new BusinessException("该订单已定向给其他制造商");
        }

        // 9) 协议文件存 IPFS + 文件哈希（不在此单独 anchor；链上凭据以 signManufacturingAgreement 为准）
        EvidenceStorageService.StoredEvidence ev = evidenceStorageService.storeWithoutAnchor(
                agreementFileBytes,
                StringUtils.hasText(agreementFilename) ? agreementFilename : "agreement.pdf");

        // 10) 构造并填充制造协议实体，准备持久化
        ManufacturingAgreement agreement = new ManufacturingAgreement();
        agreement.setOrderId(orderId.trim());
        agreement.setManufacturerId(manufacturerUserId);
        agreement.setFinalPrice(finalPrice);
        agreement.setDeliveryDate(deliveryDate);
        agreement.setAgreementHash(ev.getFileHash());
        agreement.setAgreementCid(ev.getIpfsCid());
        // 11) 若传入制造商链上地址，则记录“签署人标识”
        if (StringUtils.hasText(manufacturerBlockchainAddr)) {
            agreement.setManufacturerSign("MANUFACTURER_ADDR:" + manufacturerBlockchainAddr.trim());
        }

        // 12) 先更新数据库中的订单状态为“已接单”
        productionRequestService.update(new LambdaUpdateWrapper<ProductionRequest>()
                .eq(ProductionRequest::getOrderId, orderId.trim())
                .set(ProductionRequest::getStatus, Constants.ACCEPTED));

        agreement.setTxHash(smartContractInvokeService.signManufacturingAgreement(
                orderId.trim(),
                agreement.getAgreementHash(),
                finalPrice.toPlainString(),
                deliveryDate));
        smartContractInvokeService.updateProductionRequestStatus(orderId.trim(), Constants.ACCEPTED);

        // 13) 持久化制造协议记录，并返回给调用方
        manufacturingAgreementService.save(agreement);
        return agreement;
    }
}
