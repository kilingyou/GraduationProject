package com.scm.module.regulator.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scm.common.Result;
import com.scm.common.util.HashUtil;
import com.scm.integration.blockchain.BlockchainAnchorService;
import com.scm.integration.blockchain.ContractRoleSyncService;
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
    private final BlockchainAnchorService blockchainAnchorService;
    private final ContractRoleSyncService contractRoleSyncService;
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
        // Supplier evidence is anchored only after regulator approval.
        if (StringUtils.hasText(audit.getLicenseFileHash())) {
            blockchainAnchorService.anchor("SUPPLIER_LICENSE", audit.getLicenseFileHash());
        }
        if (StringUtils.hasText(audit.getCertFileHash())) {
            blockchainAnchorService.anchor("SUPPLIER_CERT", audit.getCertFileHash());
        }
        String submitPayload = audit.getUserId() + "|" + audit.getLicenseFileHash() + "|" + audit.getCertFileHash();
        blockchainAnchorService.anchor("SUPPLIER_AUDIT_SUBMIT", HashUtil.sha256Hex(submitPayload));

        audit.setAuditStatus("APPROVED");
        audit.setAuditorId(loginUser.getUserId());
        audit.setAuditTime(LocalDateTime.now());
        String apPayload = audit.getId() + "|" + audit.getUserId() + "|" + audit.getEnterpriseName();
        audit.setTxHash(blockchainAnchorService.anchor("SUPPLIER_APPROVE", HashUtil.sha256Hex(apPayload)));
        String digestPayload = audit.getId() + "|"
                + audit.getUserId() + "|"
                + (audit.getEnterpriseName() != null ? audit.getEnterpriseName() : "") + "|"
                + (audit.getCreditCode() != null ? audit.getCreditCode() : "") + "|"
                + (audit.getLicenseFileHash() != null ? audit.getLicenseFileHash() : "") + "|"
                + (audit.getCertFileHash() != null ? audit.getCertFileHash() : "") + "|APPROVED";
        blockchainAnchorService.anchor("SUPPLIER_AUDIT_DIGEST", HashUtil.sha256Hex(digestPayload));
        contractRoleSyncService.syncUserRoleToChain(audit.getUserId());
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
        audit.setAuditStatus("REJECTED");
        audit.setAuditorId(loginUser.getUserId());
        audit.setAuditTime(LocalDateTime.now());
        if (body != null && body.getAuditOpinion() != null) {
            audit.setAuditOpinion(body.getAuditOpinion());
        }
        String rjPayload = audit.getId() + "|REJECT|" + audit.getAuditOpinion();
        audit.setTxHash(blockchainAnchorService.anchor("SUPPLIER_REJECT", HashUtil.sha256Hex(rjPayload)));
        // Supplier rejected: clear chain role to avoid unauthorized supplier contract calls.
        contractRoleSyncService.clearUserRoleOnChain(audit.getUserId());
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
