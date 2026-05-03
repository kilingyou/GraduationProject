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

    //创建生产订单
    public void createProductionRequest(String orderId, Long targetManufacturerId, String bomHash, Integer quantity,
                                        String designDocHash, LocalDate expectedDelivery, String qualityReqHash) {
        List<Object> params = new ArrayList<>();
        params.add(empty(orderId));
        params.add(resolveAddressByUserId(targetManufacturerId));
        params.add(empty(bomHash));
        params.add(quantity == null ? 0 : quantity.longValue());
        params.add(empty(designDocHash));
        params.add(expectedDelivery == null ? 0L : expectedDelivery.atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toEpochSecond());
        params.add(empty(qualityReqHash));
        sendRequired("createProductionRequest", params);
    }

    public void signManufacturingAgreement(String orderId, String agreementHash, String priceClause, LocalDate deliveryDate) {
        List<Object> params = new ArrayList<>();
        params.add(empty(orderId));
        params.add(empty(agreementHash));
        params.add(empty(priceClause));
        params.add(deliveryDate == null ? 0L : deliveryDate.atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toEpochSecond());
        sendRequired("signManufacturingAgreement", params);
    }

    /** 与业务库 {@code bus_production_request.status} 对齐（合约不校验枚举，由业务层传入约定字符串）。 */
    public void updateProductionRequestStatus(String orderId, String status) {
        List<Object> params = new ArrayList<>();
        params.add(empty(orderId));
        params.add(empty(status));
        sendRequired("updateProductionRequestStatus", params);
    }

    public void registerDeviceRecord(String ecid, String orderId, String batchId, String devType, String testReportHash, String status) {
        List<Object> params = new ArrayList<>();
        params.add(empty(ecid));
        params.add(empty(orderId));
        params.add(empty(batchId));
        params.add(empty(devType));
        params.add(empty(testReportHash));
        params.add(empty(status));
        sendRequired("registerDeviceRecord", params);
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

    public void createAssemblyRecord(String sn, String ecidListJson, String batchNo, String fwVersion, String reportHash) {
        List<Object> params = new ArrayList<>();
        params.add(empty(sn));
        params.add(empty(ecidListJson));
        params.add(empty(batchNo));
        params.add(empty(fwVersion));
        params.add(empty(reportHash));
        sendRequired("createAssemblyRecord", params);
    }

    public void bindEcidToSn(String ecid, String sn) {
        List<Object> params = new ArrayList<>();
        params.add(empty(ecid));
        params.add(empty(sn));
        sendRequired("bindEcidToSn", params);
    }

    public void logTransfer(String sn, String trackingNo, Long receiverId, String transferType) {
        List<Object> params = new ArrayList<>();
        params.add(empty(sn));
        params.add(empty(trackingNo));
        params.add(resolveAddressByUserId(receiverId));
        params.add(empty(transferType));
        sendRequired("logTransfer", params);
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

    private void sendRequired(String functionName, List<Object> params) {
        FiscoBcosBlockchainAnchorService fisco = fiscoProvider.getIfAvailable();
        if (fisco == null || !fisco.isAvailable()) {
            throw new IllegalStateException("FISCO unavailable for function: " + functionName);
        }
        String privateKeyHex = resolveCurrentUserPrivateKeyHex();
        if (!StringUtils.hasText(privateKeyHex)) {
            throw new IllegalStateException("Current user private key missing for function: " + functionName);
        }
        try {
            String txHash = fisco.sendTransactionByPrivateKey(privateKeyHex, functionName, params);
            log.info("Smart-contract call success: {} tx={}", functionName, txHash);
        } catch (Exception e) {
            throw new RuntimeException("Smart-contract call failed: " + functionName + ", err=" + e.getMessage(), e);
        }
    }

    private String resolveCurrentUserPrivateKeyHex() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser)) {
                return "";
            }
            LoginUser loginUser = (LoginUser) authentication.getPrincipal();
            if (loginUser.getUserId() == null) {
                return "";
            }
            SysUser user = sysUserMapper.selectById(loginUser.getUserId());
            if (user == null || !StringUtils.hasText(user.getPrivateKeyEnc())) {
                return "";
            }
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
