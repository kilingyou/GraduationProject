package com.scm.module.supplier.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scm.common.PageResult;
import com.scm.common.Result;
import com.scm.module.supplier.entity.Bom;
import com.scm.module.supplier.entity.BomItem;
import com.scm.module.supplier.service.BomService;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/supplier/bom")
@RequiredArgsConstructor
public class BomController {

    private final BomService bomService;

    @PostMapping
    public Result<Bom> create(@RequestBody Bom bom) {
        LoginUser loginUser = getCurrentUser();
        bom.setSupplierId(loginUser.getUserId());

        List<BomItem> items = bom.getItems();
        Bom created = bomService.createBom(bom, items);
        return Result.ok(created);
    }

    @GetMapping("/list")
    public Result<PageResult<Bom>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        LoginUser loginUser = getCurrentUser();

        Page<Bom> page = new Page<>(pageNum, pageSize);
        IPage<Bom> result = bomService.listBySupplier(loginUser.getUserId(), page);

        PageResult<Bom> pageResult = new PageResult<Bom>()
                .setRecords(result.getRecords())
                .setTotal(result.getTotal())
                .setCurrent(result.getCurrent())
                .setSize(result.getSize());
        return Result.ok(pageResult);
    }

    @GetMapping("/{id}")
    public Result<Bom> detail(@PathVariable Long id) {
        Bom bom = bomService.getBomWithItems(id);
        if (bom == null) {
            return Result.fail("BOM not found");
        }
        return Result.ok(bom);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        boolean removed = bomService.removeById(id);
        return removed ? Result.ok() : Result.fail("Delete failed");
    }

    private LoginUser getCurrentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
