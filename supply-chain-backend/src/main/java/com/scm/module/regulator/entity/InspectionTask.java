package com.scm.module.regulator.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("bus_inspection_task")
public class InspectionTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskNo;

    private Long inspectorId;

    private String targetType;

    private String targetId;

    private String inspectionResult;

    private String reportHash;

    private String reportCid;

    private String inspectorSign;

    private String txHash;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
