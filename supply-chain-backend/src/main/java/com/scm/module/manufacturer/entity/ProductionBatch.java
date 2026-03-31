package com.scm.module.manufacturer.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("bus_production_batch")
public class ProductionBatch {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String batchId;

    private String orderId;

    private Long manufacturerId;

    private Integer plannedQty;

    private Integer completedQty;

    private String status;

    private String txHash;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
