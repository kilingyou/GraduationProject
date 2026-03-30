package com.scm.module.enduser.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("bus_decommission")
public class Decommission {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sn;

    private Long applicantId;

    private Long recyclerId;

    private String disposalMethod;

    private LocalDateTime disposalTime;

    private String recyclerName;

    private String txHash;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
