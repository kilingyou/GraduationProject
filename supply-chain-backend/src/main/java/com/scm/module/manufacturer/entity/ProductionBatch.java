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

    /** 本批次生产的 BOM 子件行；与 {@link #plannedQty} 共同约束该子件 ECID 上限 */
    private Long bomItemId;

    private Integer plannedQty;

    private Integer completedQty;

    private String status;

    private String txHash;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 列表展示用，非表字段 */
    @TableField(exist = false)
    private String bomPartSummary;
}
