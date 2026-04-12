package com.scm.module.supplier.controller;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scm.common.PageResult;
import com.scm.common.Result;
import com.scm.module.supplier.dto.BomImportRow;
import com.scm.module.supplier.dto.BomVO;
import com.scm.module.supplier.entity.Bom;
import com.scm.module.supplier.entity.BomItem;
import com.scm.module.supplier.service.BomService;
import com.scm.module.supplier.service.SupplierAuditGuardService;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/supplier/bom")
@RequiredArgsConstructor
public class BomController {

    private final BomService bomService;
    private final SupplierAuditGuardService supplierAuditGuardService;

    @PostMapping
    public Result<Bom> create(@RequestBody Bom bom) {
        LoginUser loginUser = getCurrentUser();
        //确认资质审核通过
        supplierAuditGuardService.ensureApproved(loginUser.getUserId());
        //设置用户id
        bom.setSupplierId(loginUser.getUserId());
        //物料列表
        List<BomItem> items = bom.getItems();
        //
        Bom created = bomService.createBom(bom, items);
        return Result.ok(created);
    }

    @GetMapping("/list")
    public Result<PageResult<BomVO>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        LoginUser loginUser = getCurrentUser();

        Page<Bom> page = new Page<>(pageNum, pageSize);
        IPage<BomVO> result = bomService.pageVoBySupplier(loginUser.getUserId(), page);

        PageResult<BomVO> pageResult = new PageResult<BomVO>()
                .setRecords(result.getRecords())
                .setTotal(result.getTotal())
                .setCurrent(result.getCurrent())
                .setSize(result.getSize());
        return Result.ok(pageResult);
    }

    @GetMapping("/{id}")
    public Result<BomVO> detail(@PathVariable Long id) {
        LoginUser loginUser = getCurrentUser();
        BomVO bom = bomService.getBomVoWithItems(id, loginUser.getUserId());
        if (bom == null) {
            return Result.fail("BOM 不存在");
        }
        return Result.ok(bom);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        LoginUser loginUser = getCurrentUser();
        supplierAuditGuardService.ensureApproved(loginUser.getUserId());
        bomService.removeBomForSupplier(id, loginUser.getUserId());
        return Result.ok();
    }

    @GetMapping("/import-template")
    public void downloadImportTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fn = URLEncoder.encode("BOM导入模板", StandardCharsets.UTF_8.name()).replace("+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fn + ".xlsx");
        BomImportRow demo = new BomImportRow();
        demo.setPartName("示例物料");
        demo.setPartNumber("P-001");
        demo.setSpecification("规格说明");
        demo.setQuantity(100);
        demo.setUnit("件");
        demo.setRemark("示例行可删除后填写真实数据");
        EasyExcel.write(response.getOutputStream(), BomImportRow.class).sheet("BOM").doWrite(Collections.singletonList(demo));
    }

    @PostMapping(value = "/import/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<List<BomItem>> parseImport(@RequestPart("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return Result.fail("请上传 Excel 文件");
        }
        List<BomImportRow> rows = EasyExcel.read(file.getInputStream()).head(BomImportRow.class).sheet().doReadSync();
        List<BomItem> items = new ArrayList<>();
        for (BomImportRow r : rows) {
            if (r.getPartName() == null || r.getPartName().trim().isEmpty()) {
                continue;
            }
            BomItem it = new BomItem();
            it.setPartName(r.getPartName().trim());
            it.setPartNumber(r.getPartNumber() != null ? r.getPartNumber().trim() : "");
            it.setSpecification(r.getSpecification());
            it.setQuantity(r.getQuantity() != null && r.getQuantity() > 0 ? r.getQuantity() : 1);
            it.setUnit(r.getUnit() != null ? r.getUnit().trim() : "");
            it.setRemark(r.getRemark());
            items.add(it);
        }
        if (items.isEmpty()) {
            return Result.fail("未解析到有效物料行，请确认首行为表头：物料名称、物料编号、规格型号、数量、单位、备注");
        }
        return Result.ok(items);
    }

    private LoginUser getCurrentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
