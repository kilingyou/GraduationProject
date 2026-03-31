package com.scm.module.assembler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("bus_assembly_record")
public class AssemblyRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sn;

    private String assemblyBatchNo;

    private Long assemblerId;

    /** 当前货权归属（组装完成/上链后默认组装商；物流收货后更新为分销商等） */
    private Long currentHolderId;

    @TableField("ecid_list")
    private String ecidList;

    private String firmwareVersion;

    private String testReportHash;

    private String testReportCid;

    private String testResult;

    private String assemblerSign;

    private String status;

    private String txHash;

    private String assemblyTxHash;

    private Integer chainRegistered;

    private LocalDateTime assemblyTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
