package com.scm.module.distributor.dto;

import lombok.Data;

@Data
public class LogisticsReceiveRequest {

    private Long transferId;

    private String trackingNumber;

    private String sn;
}
