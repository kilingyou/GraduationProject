package com.scm.module.manufacturer.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("bus_reject_record")
public class RejectRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ecid;

    private String batchId;

    private Long manufacturerId;

    /** 关联生产订单 order_id，供供应商侧查询处置任务 */
    private String orderId;

    private String reason;

    private String disposalType;

    private String disposalStatus;

    /** 不合格记录上链（MFG_REJECT） */
    private String txHash;

    /** 处置完结上链（退货确认 / 销毁确认） */
    private String disposalCompleteTxHash;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
