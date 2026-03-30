package com.scm.module.system.controller;

import com.scm.common.Result;
import com.scm.module.system.entity.SysMenu;
import com.scm.module.system.service.SysMenuService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system/menu")
@RequiredArgsConstructor
public class SysMenuController {

    private final SysMenuService sysMenuService;

    @GetMapping("/tree")
    public Result<List<SysMenu>> tree() {
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        List<SysMenu> tree = sysMenuService.getMenuTree(loginUser.getUserId());
        return Result.ok(tree);
    }

    @GetMapping("/list")
    public Result<List<SysMenu>> list() {
        return Result.ok(sysMenuService.listAll());
    }

    @PostMapping
    public Result<Void> add(@RequestBody SysMenu menu) {
        sysMenuService.save(menu);
        return Result.ok();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysMenu menu) {
        menu.setId(id);
        sysMenuService.updateById(menu);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysMenuService.removeById(id);
        return Result.ok();
    }
}
