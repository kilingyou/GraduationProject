package com.scm.module.manufacturer.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 不合格记录展示（供应商 / 制造商处置列表）。
 */
@Data
public class RejectRecordVO {

    private Long id;
    private String ecid;
    private String batchId;
    private Long manufacturerId;
    /** 供应商列表展示制造商名称 */
    private String manufacturerName;
    private String orderId;
    private String reason;
    private String disposalType;
    private String disposalStatus;
    private String txHash;
    private String disposalCompleteTxHash;
    private LocalDateTime createTime;
}
