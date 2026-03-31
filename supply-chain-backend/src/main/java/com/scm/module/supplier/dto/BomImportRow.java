package com.scm.module.supplier.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * BOM Excel 导入行（与模板列名一致，见 {@link com.scm.module.supplier.controller.BomController}）。
 */
@Data
public class BomImportRow {

    @ExcelProperty("物料名称")
    private String partName;

    @ExcelProperty("物料编号")
    private String partNumber;

    @ExcelProperty("规格型号")
    private String specification;

    @ExcelProperty("数量")
    private Integer quantity;

    @ExcelProperty("单位")
    private String unit;

    @ExcelProperty("备注")
    private String remark;
}
