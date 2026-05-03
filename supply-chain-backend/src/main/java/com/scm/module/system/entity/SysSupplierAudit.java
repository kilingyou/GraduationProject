package com.scm.module.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

//供应商审核表
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
    /** 准入/驳回对应的合约交易：通过为 {@code approveSupplier}，驳回为 {@code revokeSupplier}（无链上地址时可能为空）。 */
    private String txHash;
    @TableField(exist = false)
    private String licenseViewUrl;
    @TableField(exist = false)
    private String certViewUrl;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
