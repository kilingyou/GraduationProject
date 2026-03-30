package com.scm.module.manufacturer.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("bus_quality_report")
public class QualityReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String reportType;

    private String targetType;

    private String targetId;

    private Long reporterId;

    private String reportName;

    private String fileHash;

    private String ipfsCid;

    private String result;

    private String remark;

    private String signerAddr;

    private String signature;

    private String txHash;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
