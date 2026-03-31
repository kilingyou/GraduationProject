package com.scm.module.supplier.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 供应商进度跟踪：协议摘要 + ECID + 质检报告 + 状态轴时间戳（与前端 expand 结构对齐）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductionOrderTrackVO {

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AgreementSummary {
        private String manufacturerName;
        private LocalDate promisedDelivery;
        private BigDecimal agreedPrice;
        /** 占位：已签署 / 待供方签章等 */
        private String status;
        private String remark;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EcidRow {
        private String ecid;
        private String status;
        private String createTime;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ReportRow {
        private String reportName;
        private String result;
        private String createTime;
    }

    private Map<String, String> statusTimes;
    private AgreementSummary agreement;
    private List<EcidRow> ecidList;
    private List<ReportRow> testReports;
}
