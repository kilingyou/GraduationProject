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

    private String productModel;

    private Integer plannedQty;

    private Integer completedQty;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
