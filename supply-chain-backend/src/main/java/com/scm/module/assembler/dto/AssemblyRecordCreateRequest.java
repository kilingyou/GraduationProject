package com.scm.module.assembler.dto;

import lombok.Data;

import java.util.List;

@Data
public class AssemblyRecordCreateRequest {

    /** 与前端字段 batchNo 一致 */
    private String batchNo;

    private List<String> ecidList;

    private String firmwareVersion;

    /** 可选；不传则后端生成 SN */
    private String sn;
}
