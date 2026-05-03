package com.scm.module.manufacturer.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 制造商订单维度的生产进度摘要（批次 + ECID 统计），用于订单详情嵌入展示。
 */
@Data
@Accessors(chain = true)
public class ManufacturerOrderProductionSummaryVO {

    private String orderId;

    private String orderStatus;

    private Integer orderQuantity;

    private int batchCount;

    private int batchCompletedCount;

    private long ecidTotal;

    private long ecidQcPassCount;

    private long ecidOnChainCount;

    private long ecidAssembledCount;

    private List<BatchBrief> batches = new ArrayList<>();

    @Data
    @Accessors(chain = true)
    public static class BatchBrief {
        private String batchId;
        private String status;
        private Integer plannedQty;
        private Integer completedQty;
        private String bomPartSummary;
    }
}
