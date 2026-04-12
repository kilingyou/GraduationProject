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

    /** 对应 BOM 明细行（子件），组装商按 ECID 引用多子件 */
    private Long bomItemId;

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

    /** 列表展示用 */
    @TableField(exist = false)
    private String bomPartSummary;
}
