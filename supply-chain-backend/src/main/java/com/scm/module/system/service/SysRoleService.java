package com.scm.module.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.scm.module.system.entity.SysRole;

import java.util.List;

public interface SysRoleService extends IService<SysRole> {

    List<SysRole> listAllRoles();

    void assignMenus(Long roleId, List<Long> menuIds);
}
