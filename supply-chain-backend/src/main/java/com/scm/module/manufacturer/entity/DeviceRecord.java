package com.scm.module.manufacturer.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("bus_device_record")
public class DeviceRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ecid;

    private String orderId;

    private String batchId;

    private Long manufacturerId;

    private String deviceType;

    private LocalDateTime manufactureTime;

    private String status;

    private String testReportHash;

    private String testReportCid;

    private String txHash;

    private Integer chainRegistered;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
