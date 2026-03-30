package com.scm.module.supplier.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Accessors(chain = true)
@TableName("bus_bom")
public class Bom {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long supplierId;

    private String bomName;

    private Long designDocId;

    private String version;

    private String fileHash;

    private String ipfsCid;

    private String txHash;

    private String chainStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private List<BomItem> items;
}
