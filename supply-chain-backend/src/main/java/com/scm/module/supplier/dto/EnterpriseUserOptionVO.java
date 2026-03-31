package com.scm.module.supplier.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class EnterpriseUserOptionVO {

    private Long id;

    /** 用于下拉展示：优先企业名称，否则登录名 */
    private String label;
}
