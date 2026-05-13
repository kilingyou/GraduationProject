package com.scm.integration.blockchain;

import com.scm.module.system.entity.SysUser;
import com.scm.module.system.mapper.SysUserMapper;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Wraps business-level smart-contract function calls.
 * Transactions are signed by current logged-in user's private key.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmartContractInvokeService {

    private static final String ZERO_ADDRESS = "0x0000000000000000000000000000000000000000";

    private final ObjectProvider<FiscoBcosBlockchainAnchorService> fiscoProvider;
    private final SysUserMapper sysUserMapper;

    /**
     * 创建链上生产请求（合约 {@code createProductionRequest}，供应商私钥发送）。
     *
     * @return 该笔合约交易哈希，供业务表 {@code bus_production_request.tx_hash} 记录
     */
    public String createProductionRequest(String orderId, Long targetManufacturerId, String bomHash, Integer quantity,
                                          String designDocHash, LocalDate expectedDelivery, String qualityReqHash) {
        List<Object> params = new ArrayList<>();
        params.add(empty(orderId));
        params.add(resolveAddressByUserId(targetManufacturerId));
        params.add(empty(bomHash));
        params.add(quantity == null ? 0 : quantity.longValue());
        params.add(empty(designDocHash));
        params.add(expectedDelivery == null ? 0L : expectedDelivery.atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toEpochSecond());
        params.add(empty(qualityReqHash));
        return sendRequired("createProductionRequest", params);
    }

    /**
     * 制造商签署制造协议（合约 {@code signManufacturingAgreement}）。
     *
     * @return 该笔合约交易哈希，供 {@code bus_manufacturing_agreement.tx_hash} 记录
     */
    public String signManufacturingAgreement(String orderId, String agreementHash, String priceClause, LocalDate deliveryDate) {
        List<Object> params = new ArrayList<>();
        params.add(empty(orderId));
        params.add(empty(agreementHash));
        params.add(empty(priceClause));
        params.add(deliveryDate == null ? 0L : deliveryDate.atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toEpochSecond());
        return sendRequired("signManufacturingAgreement", params);
    }

    /** 与业务库 {@code bus_production_request.status} 对齐（合约不校验枚举，由业务层传入约定字符串）。 */
    public void updateProductionRequestStatus(String orderId, String status) {
        List<Object> params = new ArrayList<>();
        params.add(empty(orderId));
        params.add(empty(status));
        sendRequired("updateProductionRequestStatus", params);
    }

    /**
     * 设备 ECID 注册上链（合约 {@code registerDeviceRecord}）。
     *
     * @return 该笔合约交易哈希，供 {@code bus_device_record.tx_hash} 记录
     */
    public String registerDeviceRecord(String ecid, String orderId, String batchId, String devType, String testReportHash, String status) {
        List<Object> params = new ArrayList<>();
        params.add(empty(ecid));
        params.add(empty(orderId));
        params.add(empty(batchId));
        params.add(empty(devType));
        params.add(empty(testReportHash));
        params.add(empty(status));
        return sendRequired("registerDeviceRecord", params);
    }

    public void recordProductionComplete(String orderId, String batchId, boolean passed, String testReportHash, String signatureHash) {
        List<Object> params = new ArrayList<>();
        params.add(empty(orderId));
        params.add(empty(batchId));
        params.add(passed);
        params.add(empty(testReportHash));
        params.add(empty(signatureHash));
        sendRequired("recordProductionComplete", params);
    }

    /**
     * 组装记录主数据上链（合约 {@code createAssemblyRecord}）。
     *
     * @return 该笔合约交易哈希，供 {@code bus_assembly_record.tx_hash} / {@code assembly_tx_hash} 记录
     */
    public String createAssemblyRecord(String sn, String ecidListJson, String batchNo, String fwVersion, String reportHash) {
        List<Object> params = new ArrayList<>();
        params.add(empty(sn));
        params.add(empty(ecidListJson));
        params.add(empty(batchNo));
        params.add(empty(fwVersion));
        params.add(empty(reportHash));
        return sendRequired("createAssemblyRecord", params);
    }

    public void bindEcidToSn(String ecid, String sn) {
        List<Object> params = new ArrayList<>();
        params.add(empty(ecid));
        params.add(empty(sn));
        sendRequired("bindEcidToSn", params);
    }

    /**
     * 批量绑定 ECID→SN（合约 {@code bindEcidsToSn}），单笔交易替代多次 {@link #bindEcidToSn}。
     *
     * @return 该笔合约交易哈希（可选落库；业务主凭据仍以 {@link #createAssemblyRecord} 为准）
     */
    public String bindEcidsToSn(List<String> ecids, String sn) {
        List<Object> params = new ArrayList<>();
        params.add(ecids == null ? new ArrayList<>() : ecids);
        params.add(empty(sn));
        return sendRequired("bindEcidsToSn", params);
    }

    /**
     * 分销物流上链（合约 {@code logTransfer}）。
     *
     * @return 该笔合约交易哈希，供 {@code bus_transfer_event.tx_hash} 记录
     */
    public String logTransfer(String sn, String trackingNo, Long receiverId, String transferType) {
        List<Object> params = new ArrayList<>();
        params.add(empty(sn));
        params.add(empty(trackingNo));
        params.add(resolveAddressByUserId(receiverId));
        params.add(empty(transferType));
        return sendRequired("logTransfer", params);
    }

    public void registerSale(String sn, String customerHash, String invoiceHash) {
        List<Object> params = new ArrayList<>();
        params.add(empty(sn));
        params.add(empty(customerHash));
        params.add(empty(invoiceHash));
        sendRequired("registerSale", params);
    }

    public void requestRecall(String sn, String faultType, String faultDesc) {
        List<Object> params = new ArrayList<>();
        params.add(empty(sn));
        params.add(empty(faultType));
        params.add(empty(faultDesc));
        sendRequired("requestRecall", params);
    }

    public void publishRecallNotice(String noticeNo, String affectedSnsJson) {
        List<Object> params = new ArrayList<>();
        params.add(empty(noticeNo));
        params.add(empty(affectedSnsJson));
        sendRequired("publishRecallNotice", params);
    }

    public void triggerBatchRecall(String noticeNo, String batchId, String reason) {
        List<Object> params = new ArrayList<>();
        params.add(empty(noticeNo));
        params.add(empty(batchId));
        params.add(empty(reason));
        sendRequired("triggerBatchRecall", params);
    }

    public void decommissionWithAgency(String sn, String disposalMethod, String agency) {
        List<Object> params = new ArrayList<>();
        params.add(empty(sn));
        params.add(empty(disposalMethod));
        params.add(empty(agency));
        sendRequired("decommissionWithAgency", params);
    }

    /**
     * 监管机构准入供应商（合约 {@code approveSupplier}，需当前用户链上角色为监管方）。
     *
     * @param supplierUserId 供应商业务用户 id（解析其 {@code blockchainAddr}）
     * @param qualHash       资质摘要 SHA-256 hex（与业务侧 digest 规则一致，写入链上 {@code SupplierApproved.qualHash}）
     * @return 该笔合约交易哈希
     */
    public String approveSupplier(Long supplierUserId, String qualHash) {
        // 由业务用户 id 查数据库得到链上账户地址，合约侧只认地址不认 id
        String supplierAddr = resolveAddressByUserId(supplierUserId);
        // 注册阶段未生成地址时会落到零地址，此时无法对合约标识目标供应商
        if (ZERO_ADDRESS.equals(supplierAddr)) {
            throw new IllegalStateException("Supplier has no blockchain address: userId=" + supplierUserId);
        }
        // approveSupplier(address, qualHash)：准入目标 + 资质摘要上链存证
        List<Object> params = new ArrayList<>();
        params.add(supplierAddr);
        params.add(empty(qualHash));
        // 使用当前登录监管用户私钥签名发交易（见 sendRequired）
        return sendRequired("approveSupplier", params);
    }

    /**
     * 撤销链上供应商准入（与 {@link #approveSupplier} 配对）。
     *
     * @return 交易哈希；若供应商无链上地址无法发交易则返回 {@code null}
     */
    public String revokeSupplier(Long supplierUserId) {
        String supplierAddr = resolveAddressByUserId(supplierUserId);
        if (ZERO_ADDRESS.equals(supplierAddr)) {
            log.warn("Skip revokeSupplier: no blockchain address for userId={}", supplierUserId);
            return null;
        }
        List<Object> params = new ArrayList<>();
        params.add(supplierAddr);
        return sendRequired("revokeSupplier", params);
    }

    /**
     * 以当前登录用户的链上私钥向合约发送交易（需 FISCO 已就绪）。
     */
    private String sendRequired(String functionName, List<Object> params) {
        // 仅在 scm.blockchain.mode=fisco 且 SDK 初始化成功时可用
        FiscoBcosBlockchainAnchorService fisco = fiscoProvider.getIfAvailable();
        if (fisco == null || !fisco.isAvailable()) {
            throw new IllegalStateException("FISCO unavailable for function: " + functionName);
        }
        // 从 SecurityContext 对应用户记录中解密得到 hex 私钥，用于本地签名
        String privateKeyHex = resolveCurrentUserPrivateKeyHex();
        if (!StringUtils.hasText(privateKeyHex)) {
            throw new IllegalStateException("Current user private key missing for function: " + functionName);
        }
        try {
            // 指定私钥发交易，msg.sender 在链上为当前用户对应地址
            String txHash = fisco.sendTransactionByPrivateKey(privateKeyHex, functionName, params);
            log.info("Smart-contract call success: {} tx={}", functionName, txHash);
            return txHash;
        } catch (Exception e) {
            // 统一包装为运行时异常，便于上层事务回滚或返回错误信息
            throw new RuntimeException("Smart-contract call failed: " + functionName + ", err=" + e.getMessage(), e);
        }
    }

    /**
     * 解析当前 HTTP 请求对应登录用户在库中存放的链上私钥（hex 字符串）。
     * 未登录、非 {@link LoginUser}、无用户 id、无加密私钥字段或解码失败时返回空串，由调用方判定不可用。
     */
    private String resolveCurrentUserPrivateKeyHex() {
        try {
            // Spring Security 会话：JWT 过滤器通过后此处可拿到已认证主体
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser)) {
                return "";
            }
            LoginUser loginUser = (LoginUser) authentication.getPrincipal();
            if (loginUser.getUserId() == null) {
                return "";
            }
            // 链上地址与私钥随用户注册写入 sys_user；此处按业务用户 id 再查一行拿密文字段
            SysUser user = sysUserMapper.selectById(loginUser.getUserId());
            if (user == null || !StringUtils.hasText(user.getPrivateKeyEnc())) {
                return "";
            }
            // Base64 解码得到 hex 私钥（见 decodePrivateKey）；供 SDK 本地签名发交易
            return decodePrivateKey(user.getPrivateKeyEnc());
        } catch (Exception e) {
            log.warn("Resolve current user private key failed: {}", e.getMessage());
            return "";
        }
    }

    private String resolveAddressByUserId(Long userId) {
        if (userId == null) {
            return ZERO_ADDRESS;
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || !StringUtils.hasText(user.getBlockchainAddr())) {
            return ZERO_ADDRESS;
        }
        String addr = user.getBlockchainAddr().trim();
        if (addr.matches("^0x[0-9a-fA-F]{40}$")) {
            return addr;
        }
        return ZERO_ADDRESS;
    }

    private String empty(String value) {
        return value == null ? "" : value;
    }

    private String decodePrivateKey(String privateKeyEnc) {
        if (!StringUtils.hasText(privateKeyEnc)) {
            return "";
        }
        try {
            byte[] raw = Base64.getDecoder().decode(privateKeyEnc.trim());
            return new String(raw, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            // Backward compatibility: some old records may store plain hex.
            return privateKeyEnc.trim();
        }
    }
}
