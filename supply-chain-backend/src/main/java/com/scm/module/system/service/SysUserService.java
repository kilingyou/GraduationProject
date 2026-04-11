package com.scm.module.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.scm.module.system.entity.SysUser;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface SysUserService extends IService<SysUser> {

    void register(SysUser user, String roleKey);

    /**
     * Supplier registration may attach qualification files (e.g. license, certificate); stored via evidence pipeline.
     */
    void register(SysUser user, String roleKey, List<MultipartFile> qualificationFiles);

    SysUser getUserInfo(Long userId);

    void updateProfile(SysUser user);

    void assignRole(Long userId, String roleKey);

    SysUser initBlockchainAccountIfMissing(Long userId);

    IPage<SysUser> listUsers(Page<SysUser> page, String username, String roleKey);
}
