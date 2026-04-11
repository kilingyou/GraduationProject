package com.scm.module.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scm.common.PageResult;
import com.scm.common.Result;
import com.scm.integration.blockchain.ContractRoleSyncService;
import com.scm.module.system.entity.SysUser;
import com.scm.module.system.service.SysUserService;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService sysUserService;
    private final ContractRoleSyncService contractRoleSyncService;

    @GetMapping("/list")
    public Result<PageResult<SysUser>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String roleKey) {

        IPage<SysUser> result = sysUserService.listUsers(new Page<>(page, size), username, roleKey);
        PageResult<SysUser> pageResult = new PageResult<SysUser>()
                .setRecords(result.getRecords())
                .setTotal(result.getTotal())
                .setCurrent(result.getCurrent())
                .setSize(result.getSize());
        return Result.ok(pageResult);
    }

    @GetMapping("/{id}")
    public Result<SysUser> detail(@PathVariable Long id) {
        SysUser user = sysUserService.getUserInfo(id);
        return Result.ok(user);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysUser user) {
        user.setId(id);
        sysUserService.updateProfile(user);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setDelFlag(1);
        sysUserService.updateById(user);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        SysUser existing = sysUserService.getById(id);
        if (existing == null) {
            return Result.fail("用户不存在");
        }
        SysUser update = new SysUser();
        update.setId(id);
        update.setStatus(existing.getStatus() == 1 ? 0 : 1);
        sysUserService.updateById(update);
        return Result.ok();
    }

    @PutMapping("/{id}/role")
    public Result<Void> assignRole(@PathVariable Long id, @RequestParam String roleKey) {
        Result<Void> check = requireAdmin();
        if (check.getCode() != 200) {
            return Result.fail(check.getMessage());
        }
        sysUserService.assignRole(id, roleKey);
        return Result.ok();
    }

    @PutMapping("/{id}/blockchain-account/init")
    public Result<SysUser> initBlockchainAccount(@PathVariable Long id) {
        Result<Void> check = requireAdmin();
        if (check.getCode() != 200) {
            return Result.fail(check.getMessage());
        }
        return Result.ok(sysUserService.initBlockchainAccountIfMissing(id));
    }

    @GetMapping("/role-consistency")
    public Result<List<Map<String, Object>>> roleConsistency(
            @RequestParam(required = false) String roleKey,
            @RequestParam(defaultValue = "50") Integer limit) {
        int cap = Math.max(1, Math.min(limit, 200));
        IPage<SysUser> page = sysUserService.listUsers(new Page<>(1, cap), null, roleKey);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SysUser u : page.getRecords()) {
            rows.add(contractRoleSyncService.checkUserRoleConsistency(u.getId()));
        }
        return Result.ok(rows);
    }

    @PostMapping("/role-consistency/repair")
    public Result<Map<String, Object>> repairRoleConsistency(
            @RequestParam(required = false) String roleKey,
            @RequestParam(defaultValue = "50") Integer limit,
            @RequestParam(defaultValue = "true") boolean onlyInconsistent) {
        Result<Void> check = requireAdmin();
        if (check.getCode() != 200) {
            return Result.<Map<String, Object>>fail(check.getMessage());
        }
        int cap = Math.max(1, Math.min(limit, 500));
        IPage<SysUser> page = sysUserService.listUsers(new Page<>(1, cap), null, roleKey);
        List<Long> userIds = new ArrayList<>();
        for (SysUser u : page.getRecords()) {
            if (u.getId() != null) {
                userIds.add(u.getId());
            }
        }
        return Result.ok(contractRoleSyncService.repairUsersRoleConsistency(userIds, onlyInconsistent));
    }

    private Result<Void> requireAdmin() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof LoginUser)) {
            return Result.fail("未登录或登录态失效");
        }
        LoginUser loginUser = (LoginUser) principal;
        if (!"admin".equalsIgnoreCase(loginUser.getRoleKey())) {
            return Result.fail("仅管理员可执行该操作");
        }
        return Result.ok();
    }

}
