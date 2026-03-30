package com.scm.module.regulator.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("bus_recall_notice")
public class RecallNotice {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String noticeNo;

    private Long issuerId;

    private String faultSourceSn;

    private String faultBatchId;

    private String faultEcid;

    @TableField("affected_sns")
    private String affectedSns;

    private String disposalPlan;

    private String status;

    private String txHash;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
