package com.scm.module.supplier.dto;

import com.scm.module.supplier.entity.Bom;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 供应商侧 BOM 展示：补充关联设计文档名称。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class BomVO extends Bom {

    private String designDocName;
}
