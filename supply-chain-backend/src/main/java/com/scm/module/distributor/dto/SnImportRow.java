package com.scm.module.distributor.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class SnImportRow {

    @ExcelProperty("SN")
    private String sn;
}
