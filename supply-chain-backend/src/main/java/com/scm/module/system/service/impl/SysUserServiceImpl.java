package com.scm.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scm.common.exception.BusinessException;
import com.scm.integration.blockchain.BlockchainAnchorService;
import com.scm.integration.evidence.EvidenceStorageService;
import com.scm.module.system.entity.SysRole;
import com.scm.module.system.entity.SysSupplierAudit;
import com.scm.module.system.entity.SysUser;
import com.scm.module.system.mapper.SysRoleMapper;
import com.scm.module.system.mapper.SysSupplierAuditMapper;
import com.scm.module.system.mapper.SysUserMapper;
import com.scm.module.system.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;


@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final SysRoleMapper sysRoleMapper;
    private final SysSupplierAuditMapper sysSupplierAuditMapper;
    private final PasswordEncoder passwordEncoder;
    private final EvidenceStorageService evidenceStorageService;
    private final BlockchainAnchorService blockchainAnchorService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(SysUser user, String roleKey) {
        register(user, roleKey, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(SysUser user, String roleKey, List<MultipartFile> qualificationFiles) {
        Long existCount = baseMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, user.getUsername()));
        if (existCount > 0) {
            throw new BusinessException("用户名已存在");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setStatus(1);
        user.setDelFlag(0);
        BlockchainAnchorService.BlockchainAccount account = blockchainAnchorService.generateBlockchainAccount();
        user.setBlockchainAddr(account.getAddress());
        // Existing column is named privateKeyEnc; use Base64 to avoid plain-text storage.
        if (StringUtils.hasText(account.getPrivateKeyHex())) {
            user.setPrivateKeyEnc(Base64.getEncoder().encodeToString(account.getPrivateKeyHex().getBytes(StandardCharsets.UTF_8)));
        }
        baseMapper.insert(user);

        if ("supplier".equalsIgnoreCase(roleKey)) {
            SysSupplierAudit audit = new SysSupplierAudit();
            audit.setUserId(user.getId());
            audit.setEnterpriseName(user.getEnterpriseName());
            audit.setCreditCode(user.getCreditCode());
            audit.setAuditStatus("PENDING");

            List<MultipartFile> files = qualificationFiles != null ? qualificationFiles : Collections.emptyList();
            for (int i = 0; i < files.size(); i++) {
                MultipartFile f = files.get(i);
                if (f == null || f.isEmpty()) {
                    continue;
                }
                byte[] bytes;
                try {
                    bytes = f.getBytes();
                } catch (IOException e) {
                    throw new BusinessException("资质文件读取失败");
                }
                String biz = i == 0 ? "SUPPLIER_LICENSE" : "SUPPLIER_CERT";
                EvidenceStorageService.StoredEvidence ev =
                        evidenceStorageService.store(bytes, f.getOriginalFilename(), biz);
                if (i == 0) {
                    audit.setLicenseFileHash(ev.getFileHash());
                    audit.setLicenseIpfsCid(ev.getIpfsCid());
                } else if (i == 1) {
                    audit.setCertFileHash(ev.getFileHash());
                    audit.setCertIpfsCid(ev.getIpfsCid());
                }
            }
            sysSupplierAuditMapper.insert(audit);
        }

        if (StringUtils.hasText(roleKey)) {
            SysRole role = sysRoleMapper.selectOne(
                    new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleKey, roleKey));
            if (role != null) {
                baseMapper.insertUserRole(user.getId(), role.getId());
            }
        }
    }

    @Override
    public SysUser getUserInfo(Long userId) {
        SysUser user = baseMapper.selectByIdWithRole(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if ("supplier".equalsIgnoreCase(user.getRoleKey())) {
            SysSupplierAudit audit = sysSupplierAuditMapper.selectOne(
                    new LambdaQueryWrapper<SysSupplierAudit>()
                            .eq(SysSupplierAudit::getUserId, userId)
                            .orderByDesc(SysSupplierAudit::getCreateTime)
                            .last("LIMIT 1"));
            user.setSupplierAuditStatus(audit == null ? "PENDING" : audit.getAuditStatus());
        }
        user.setPassword(null);
        return user;
    }

    @Override
    public void updateProfile(SysUser user) {
        user.setPassword(null);
        user.setUsername(null);
        user.setDelFlag(null);
        user.setStatus(null);
        baseMapper.updateById(user);
    }

    @Override
    public IPage<SysUser> listUsers(Page<SysUser> page, String username, String roleKey) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(username), SysUser::getUsername, username);
        wrapper.eq(SysUser::getDelFlag, 0);
        wrapper.orderByDesc(SysUser::getCreateTime);
        IPage<SysUser> result = baseMapper.selectPage(page, wrapper);
        result.getRecords().forEach(u -> u.setPassword(null));
        return result;
    }
}
