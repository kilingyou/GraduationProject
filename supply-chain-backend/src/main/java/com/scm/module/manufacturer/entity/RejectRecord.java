package com.scm.module.manufacturer.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("bus_reject_record")
public class RejectRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ecid;

    private String batchId;

    private Long manufacturerId;

    private String reason;

    private String disposalType;

    private String disposalStatus;

    private String txHash;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
