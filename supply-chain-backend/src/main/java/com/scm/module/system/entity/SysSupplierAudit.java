package com.scm.module.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_supplier_audit")
public class SysSupplierAudit {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String enterpriseName;
    private String creditCode;
    private String licenseFileHash;
    private String licenseIpfsCid;
    private String certFileHash;
    private String certIpfsCid;
    private String auditStatus;
    private Long auditorId;
    private String auditOpinion;
    private LocalDateTime auditTime;
    private String txHash;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
