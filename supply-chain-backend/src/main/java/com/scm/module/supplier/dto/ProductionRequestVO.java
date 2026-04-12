package com.scm.module.supplier.dto;

import com.scm.module.supplier.entity.ProductionRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 供应商侧生产订单展示：在 {@link ProductionRequest} 基础上补充 BOM、设计文档与定向制造商名称。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class ProductionRequestVO extends ProductionRequest {

    private String bomName;

    private String designDocName;

    /** 定向制造商企业名或登录名（无则空） */
    private String targetManufacturerName;

    /** 指定组装商企业名或登录名（无则空） */
    private String assemblyAssemblerName;
}
