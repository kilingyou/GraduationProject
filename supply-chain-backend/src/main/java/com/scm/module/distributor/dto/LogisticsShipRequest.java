package com.scm.module.distributor.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LogisticsShipRequest {

    private String sn;

    private String logisticsCompany;

    /** 与前端 trackingNo 对齐时由 Controller 映射 */
    private String trackingNumber;

    private Long receiverId;

    private LocalDateTime shipTime;

    private LocalDateTime estimatedArrival;

    private String transferType;
}
