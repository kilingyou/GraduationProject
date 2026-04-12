package com.scm.module.supplier.controller;

import com.scm.common.Result;
import com.scm.module.supplier.dto.EnterpriseUserOptionVO;
import com.scm.module.system.entity.SysUser;
import com.scm.module.system.mapper.SysUserMapper;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 供应商侧：可选组装商账号（与定向制造商列表用法一致）。
 */
@RestController
@RequestMapping("/api/supplier/assemblers")
@RequiredArgsConstructor
public class SupplierAssemblerOptionsController {

    private final SysUserMapper sysUserMapper;

    @GetMapping
    public Result<List<EnterpriseUserOptionVO>> listAssemblers() {
        getCurrentUser();
        List<SysUser> users = sysUserMapper.listActiveUsersByRoleKey("assembler");
        List<EnterpriseUserOptionVO> options = users.stream()
                .map(u -> {
                    EnterpriseUserOptionVO vo = new EnterpriseUserOptionVO();
                    vo.setId(u.getId());
                    String label = StringUtils.hasText(u.getEnterpriseName())
                            ? u.getEnterpriseName().trim()
                            : u.getUsername();
                    vo.setLabel(label);
                    return vo;
                })
                .collect(Collectors.toList());
        return Result.ok(options);
    }

    private LoginUser getCurrentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
