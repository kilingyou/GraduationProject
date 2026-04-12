package com.scm.module.assembler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("bus_assembly_batch")
public class AssemblyBatch {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String batchNo;

    private Long assemblerId;

    /** 关联生产订单业务号（与 bus_production_request.order_id 一致） */
    private String orderId;

    private String productModel;

    private Integer plannedQty;

    private Integer completedQty;

    private String status;

    private String txHash;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
