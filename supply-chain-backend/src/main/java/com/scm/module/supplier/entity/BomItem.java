package com.scm.module.supplier.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@TableName("bus_bom_item")
public class BomItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long bomId;

    @JsonAlias("materialName")
    private String partName;

    @JsonAlias("materialCode")
    private String partNumber;

    private String specification;

    private Integer quantity;

    private String unit;

    private String remark;
}
