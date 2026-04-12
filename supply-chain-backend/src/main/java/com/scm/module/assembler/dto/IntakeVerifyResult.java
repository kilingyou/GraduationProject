package com.scm.module.assembler.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class IntakeVerifyResult {

    public static final String PASS = "PASS";
    public static final String REJECT = "REJECT";
    public static final String NOT_FOUND = "NOT_FOUND";

    private String status;

    private String ecid;

    /** 展示用说明 */
    private String message;

    private String deviceType;

    private String manufacturerBatchId;

    private Integer chainRegistered;

    /** 若已绑定整机，此处为 SN */
    private String boundToSn;

    /** 所属生产订单业务号 */
    private String orderId;

    private Long bomItemId;

    /** BOM 行摘要（料号/名称） */
    private String bomPartSummary;
}
