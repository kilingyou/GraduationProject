package com.scm.module.supplier.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("bus_production_request")
public class ProductionRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderId;

    private Long supplierId;

    private Long bomId;

    private Long designDocId;

    private String designDocHash;

    private Integer quantity;

    private LocalDate expectedDelivery;

    private String qualityRequirement;

    private Long targetManufacturer;

    /** 指定组装商；NULL 表示任意组装商可领用该单下已放行部件 */
    private Long assemblyAssemblerId;

    private String status;

    private String txHash;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
