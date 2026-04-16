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

        // 9) 将制造协议文件进行存证（如文件哈希、IPFS CID 等），形成可追溯证据
        EvidenceStorageService.StoredEvidence ev = evidenceStorageService.store(
                agreementFileBytes,
                StringUtils.hasText(agreementFilename) ? agreementFilename : "agreement.pdf",
                "MANUFACTURING_AGREEMENT_FILE");

        // 10) 组装锚定载荷：订单号 + 制造商 + 最终报价 + 交期 + 协议文件哈希
        //     该载荷会做 SHA-256 后上链，作为“本次接单协议”摘要凭证
        String fileHashPart = StringUtils.hasText(ev.getFileHash()) ? ev.getFileHash() : "";
        String payload = orderId + "|" + manufacturerUserId + "|" + finalPrice + "|" + deliveryDate + "|" + fileHashPart;

        // 11) 构造并填充制造协议实体，准备持久化
        ManufacturingAgreement agreement = new ManufacturingAgreement();
        agreement.setOrderId(orderId.trim());
        agreement.setManufacturerId(manufacturerUserId);
        agreement.setFinalPrice(finalPrice);
        agreement.setDeliveryDate(deliveryDate);
        agreement.setAgreementHash(ev.getFileHash());
        agreement.setAgreementCid(ev.getIpfsCid());
        // 12) 若传入制造商链上地址，则记录“签署人标识”
        //     这里用 MANUFACTURER_ADDR: 前缀区分签名来源类型
        if (StringUtils.hasText(manufacturerBlockchainAddr)) {
            agreement.setManufacturerSign("MANUFACTURER_ADDR:" + manufacturerBlockchainAddr.trim());
        }

        // 13) 先更新数据库中的订单状态为“已接单”
        //     该方法受事务保护，若后续步骤失败会整体回滚
        productionRequestService.update(new LambdaUpdateWrapper<ProductionRequest>()
                .eq(ProductionRequest::getOrderId, orderId.trim())
                .set(ProductionRequest::getStatus, Constants.ACCEPTED));

        // 14) 将协议摘要做锚定上链，返回交易哈希 txHash（用于审计/追溯）
        agreement.setTxHash(blockchainAnchorService.anchor(
                "MANUFACTURING_AGREEMENT", HashUtil.sha256Hex(payload)));
        // 15) 调用业务智能合约登记制造协议核心信息
        //     包含：订单号、协议文件哈希、最终报价、交期
        smartContractInvokeService.signManufacturingAgreement(
                orderId.trim(),
                agreement.getAgreementHash(),
                finalPrice.toPlainString(),
                deliveryDate);
        // 16) 将订单链上状态同步为 ACCEPTED，确保链上链下状态一致
        smartContractInvokeService.updateProductionRequestStatus(orderId.trim(), Constants.ACCEPTED);

        // 17) 持久化制造协议记录，并返回给调用方
        manufacturingAgreementService.save(agreement);
        return agreement;
    }
}
