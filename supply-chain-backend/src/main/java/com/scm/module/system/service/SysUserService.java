package com.scm.module.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.scm.module.system.entity.SysUser;

public interface SysUserService extends IService<SysUser> {

    void register(SysUser user, String roleKey);

    SysUser getUserInfo(Long userId);

    void updateProfile(SysUser user);

    IPage<SysUser> listUsers(Page<SysUser> page, String username, String roleKey);
}
