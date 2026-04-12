package com.scm.module.assembler.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 与部件入库扫码/导入校验通过条件一致：质检合格、已上链、且未绑定任何组装记录；
 * 并受生产订单「指定组装商」策略过滤。
 */
@Data
@Accessors(chain = true)
public class AvailableAssemblyEcidItem {

    private String ecid;
    private String deviceType;
    private String batchId;
    private String orderId;
    private Long bomItemId;
    private String bomPartSummary;
}
