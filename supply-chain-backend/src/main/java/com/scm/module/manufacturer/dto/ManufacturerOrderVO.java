package com.scm.module.manufacturer.dto;

import com.scm.module.supplier.entity.ProductionRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 制造商侧订单展示：BOM/设计文档摘要、供应商名称、设计文件下载入口（IPFS）。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class ManufacturerOrderVO extends ProductionRequest {

    private String bomName;

    private String designDocName;

    /** 设计文档 SHA-256，供前端校验展示 */
    private String designDocFileHash;

    /** 原始上传文件名，供前端另存为 */
    private String designDocFileName;

    /** IPFS CID（原始），网关为空时仍可拼接 */
    private String designDocIpfsCid;

    /** 完整下载 URL（配置了 scm.ipfs.gateway 时生成） */
    private String designDocDownloadUrl;

    private String supplierEnterpriseName;
}
