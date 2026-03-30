package com.scm.module.distributor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("bus_sales_record")
public class SalesRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sn;

    private Long sellerId;

    private LocalDateTime saleTime;

    private String customerHash;

    private String customerNameEnc;

    private String customerPhoneEnc;

    private String invoiceHash;

    private String invoiceCid;

    private String txHash;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
