package com.scm.module.manufacturer.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("bus_manufacturing_agreement")
public class ManufacturingAgreement {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderId;

    private Long manufacturerId;

    private BigDecimal finalPrice;

    private LocalDate deliveryDate;

    private String agreementHash;

    private String agreementCid;

    private String manufacturerSign;

    private String supplierSign;

    private String txHash;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
