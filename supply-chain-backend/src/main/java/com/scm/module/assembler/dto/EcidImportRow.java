package com.scm.module.assembler.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class EcidImportRow {

    @ExcelProperty("ECID")
    private String ecid;
}
