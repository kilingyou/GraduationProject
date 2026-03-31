package com.scm.module.distributor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("bus_transfer_event")
public class TransferEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sn;

    private String batchNo;

    private String trackingNumber;

    private String logisticsCompany;

    private Long senderId;

    private Long receiverId;

    private String transferType;

    private LocalDateTime shipTime;

    private LocalDateTime estimatedArrival;

    private LocalDateTime actualArrival;

    private String txHash;

    private String receiveTxHash;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
