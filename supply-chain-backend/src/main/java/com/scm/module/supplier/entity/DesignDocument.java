package com.scm.module.supplier.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("bus_design_document")
public class DesignDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long supplierId;

    private String docName;

    private String docType;

    private String version;

    private String updateNote;

    private String fileHash;

    private String ipfsCid;

    private Long fileSize;

    private String fileName;

    private String txHash;

    private String chainStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
