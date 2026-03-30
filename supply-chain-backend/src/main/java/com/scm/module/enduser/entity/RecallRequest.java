package com.scm.module.enduser.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("bus_recall_request")
public class RecallRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String requestNo;

    private Long userId;

    private String sn;

    private String faultType;

    private String faultDesc;

    @TableField("evidence_urls")
    private String evidenceUrls;

    private String status;

    @TableField("affected_sns")
    private String affectedSns;

    private String txHash;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
