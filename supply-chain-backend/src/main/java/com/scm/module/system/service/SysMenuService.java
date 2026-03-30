package com.scm.module.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.scm.module.system.entity.SysMenu;

import java.util.List;

public interface SysMenuService extends IService<SysMenu> {

    List<SysMenu> getMenuTree(Long userId);

    List<SysMenu> listAll();
}
