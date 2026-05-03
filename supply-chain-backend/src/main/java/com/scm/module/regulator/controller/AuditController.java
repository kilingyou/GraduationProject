package com.scm.module.regulator.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scm.common.Result;
import com.scm.common.util.HashUtil;
import com.scm.integration.blockchain.ContractRoleSyncService;
import com.scm.integration.blockchain.SmartContractInvokeService;
import com.scm.integration.ipfs.IpfsStorageService;
import com.scm.module.system.entity.SysSupplierAudit;
import com.scm.module.system.mapper.SysSupplierAuditMapper;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/regulator/audit")
@RequiredArgsConstructor
public class AuditController {

    private final SysSupplierAuditMapper sysSupplierAuditMapper;
    private final ContractRoleSyncService contractRoleSyncService;
    private final SmartContractInvokeService smartContractInvokeService;
    private final IpfsStorageService ipfsStorageService;
    @Value("${scm.ipfs.gateway:}")
    private String ipfsGateway;

    //过滤approved的账号，显示未审核供应商账号返回前端
    @GetMapping("/list")
    public Result<List<SysSupplierAudit>> list(@RequestParam(required = false) String status) {
        LambdaQueryWrapper<SysSupplierAudit> wrapper = new LambdaQueryWrapper<SysSupplierAudit>()
                .orderByDesc(SysSupplierAudit::getCreateTime);
        if (StringUtils.hasText(status)) {
            wrapper.eq(SysSupplierAudit::getAuditStatus, status.trim().toUpperCase());
        }
        List<SysSupplierAudit> audits = sysSupplierAuditMapper.selectList(wrapper);
        for (SysSupplierAudit audit : audits) {
            audit.setLicenseViewUrl(buildIpfsViewUrl(audit.getLicenseIpfsCid()));
            audit.setCertViewUrl(buildIpfsViewUrl(audit.getCertIpfsCid()));
        }
        return Result.ok(audits);
    }

    //供应商资质审核模块
    @PostMapping("/{id}/approve")
    public Result<SysSupplierAudit> approve(@PathVariable Long id) {
        LoginUser loginUser = getCurrentUser();
        SysSupplierAudit audit = sysSupplierAuditMapper.selectById(id);
        if (audit == null) {
            return Result.fail("Audit record not found");
        }
        if (!"PENDING".equalsIgnoreCase(audit.getAuditStatus())) {
            return Result.fail("Audit already processed: " + audit.getAuditStatus());
        }
        // 资质摘要哈希：写入合约 qualHash 与链下库表 tx_hash 对应的 approveSupplier 交易一一对应（不再额外 anchor）
        String digestPayload = audit.getId() + "|"
                + audit.getUserId() + "|"
                + (audit.getEnterpriseName() != null ? audit.getEnterpriseName() : "") + "|"
                + (audit.getCreditCode() != null ? audit.getCreditCode() : "") + "|"
                + (audit.getLicenseFileHash() != null ? audit.getLicenseFileHash() : "") + "|"
                + (audit.getCertFileHash() != null ? audit.getCertFileHash() : "") + "|APPROVED";
        String digestHash = HashUtil.sha256Hex(digestPayload);
        audit.setTxHash(smartContractInvokeService.approveSupplier(audit.getUserId(), digestHash));

        contractRoleSyncService.syncUserRoleToChain(audit.getUserId());

        // 链上全部成功后再落库审核结论，避免中间失败时内存态已显示通过
        audit.setAuditStatus("APPROVED");
        audit.setAuditorId(loginUser.getUserId());
        audit.setAuditTime(LocalDateTime.now());
        sysSupplierAuditMapper.updateById(audit);
        return Result.ok(audit);
    }

    @PostMapping("/{id}/reject")
    public Result<SysSupplierAudit> reject(@PathVariable Long id,
                                           @RequestBody(required = false) SysSupplierAudit body) {
        LoginUser loginUser = getCurrentUser();
        SysSupplierAudit audit = sysSupplierAuditMapper.selectById(id);
        if (audit == null) {
            return Result.fail("Audit record not found");
        }
        if (!"PENDING".equalsIgnoreCase(audit.getAuditStatus())) {
            return Result.fail("Audit already processed: " + audit.getAuditStatus());
        }
        if (body != null && body.getAuditOpinion() != null) {
            audit.setAuditOpinion(body.getAuditOpinion());
        }
        audit.setTxHash(smartContractInvokeService.revokeSupplier(audit.getUserId()));
        contractRoleSyncService.clearUserRoleOnChain(audit.getUserId());

        audit.setAuditStatus("REJECTED");
        audit.setAuditorId(loginUser.getUserId());
        audit.setAuditTime(LocalDateTime.now());
        sysSupplierAuditMapper.updateById(audit);
        return Result.ok(audit);
    }

    @GetMapping("/file/{cid}")
    public ResponseEntity<byte[]> viewFile(@PathVariable String cid) {
        byte[] data = ipfsStorageService.get(cid);
        if (data == null || data.length == 0) {
            return ResponseEntity.notFound().build();
        }

        String guessed = null;
        try {
            guessed = java.net.URLConnection.guessContentTypeFromStream(new ByteArrayInputStream(data));
        } catch (Exception ignore) {
        }
        MediaType mediaType = StringUtils.hasText(guessed) ? MediaType.parseMediaType(guessed) : MediaType.APPLICATION_OCTET_STREAM;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentDisposition(ContentDisposition.inline().filename(cid).build());
        return ResponseEntity.ok().headers(headers).body(data);
    }

    private LoginUser getCurrentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private String buildIpfsViewUrl(String cid) {
        if (!StringUtils.hasText(cid) || !StringUtils.hasText(ipfsGateway)) {
            return null;
        }
        String base = ipfsGateway.endsWith("/") ? ipfsGateway : ipfsGateway + "/";
        return base + cid;
    }
}
