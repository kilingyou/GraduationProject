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

    /** 1=匿名销售：链上仅存摘要，不落明文 */
    private Integer customerAnonymous;

    /** B2B / B2C */
    private String customerSegment;

    private String invoiceHash;

    private String invoiceCid;

    private String txHash;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
