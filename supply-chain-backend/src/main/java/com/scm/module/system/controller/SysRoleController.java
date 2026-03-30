package com.scm.module.system.controller;

import com.scm.common.Result;
import com.scm.module.system.entity.SysRole;
import com.scm.module.system.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService sysRoleService;

    @GetMapping("/list")
    public Result<List<SysRole>> list() {
        return Result.ok(sysRoleService.listAllRoles());
    }

    @PostMapping("/menu/{roleId}")
    public Result<Void> assignMenus(@PathVariable Long roleId, @RequestBody List<Long> menuIds) {
        sysRoleService.assignMenus(roleId, menuIds);
        return Result.ok();
    }
}
