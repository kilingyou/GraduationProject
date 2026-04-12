-- ============================================================
-- 全流程可追溯的电子产品供应链管理系统 - 数据库设计
-- MySQL 8.0
-- ============================================================

CREATE DATABASE IF NOT EXISTS supply_chain_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE supply_chain_db;

-- ============================================================
-- 一、系统权限模块 (RBAC)
-- ============================================================

-- 用户表
CREATE TABLE sys_user (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    username        VARCHAR(64)  NOT NULL UNIQUE COMMENT '登录账号',
    password        VARCHAR(255) NOT NULL COMMENT '加密密码',
    enterprise_name VARCHAR(200) COMMENT '企业名称',
    credit_code     VARCHAR(64)  COMMENT '统一社会信用代码',
    contact_person  VARCHAR(64)  COMMENT '联系人',
    phone           VARCHAR(32)  COMMENT '联系电话',
    email           VARCHAR(128) COMMENT '邮箱',
    avatar          VARCHAR(500) COMMENT '头像URL',
    blockchain_addr VARCHAR(128) COMMENT '区块链公钥地址',
    private_key_enc TEXT         COMMENT '加密后的私钥(KeyStore)',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '0-禁用 1-正常',
    parent_id       BIGINT       DEFAULT NULL COMMENT '主账号ID(子账号场景)',
    del_flag        TINYINT      NOT NULL DEFAULT 0,
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_credit_code (credit_code),
    INDEX idx_blockchain_addr (blockchain_addr)
) COMMENT '用户表';

-- 角色表
CREATE TABLE sys_role (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_key    VARCHAR(64)  NOT NULL UNIQUE COMMENT '角色标识: supplier/manufacturer/assembler/distributor/regulator/enduser',
    role_name   VARCHAR(100) NOT NULL COMMENT '角色名称',
    sort_order  INT          DEFAULT 0,
    status      TINYINT      NOT NULL DEFAULT 1,
    remark      VARCHAR(500),
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '角色表';

-- 菜单/权限表
CREATE TABLE sys_menu (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_id   BIGINT       DEFAULT 0 COMMENT '父菜单ID',
    menu_name   VARCHAR(100) NOT NULL COMMENT '菜单名称',
    path        VARCHAR(200) COMMENT '路由路径',
    component   VARCHAR(255) COMMENT '前端组件路径',
    perms       VARCHAR(200) COMMENT '权限标识 如 supplier:design:upload',
    menu_type   CHAR(1)      NOT NULL COMMENT 'M-目录 C-菜单 F-按钮',
    icon        VARCHAR(100) COMMENT '图标',
    sort_order  INT          DEFAULT 0,
    visible     TINYINT      DEFAULT 1 COMMENT '是否显示',
    status      TINYINT      DEFAULT 1,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_parent_id (parent_id)
) COMMENT '菜单权限表';

-- 用户-角色关联表
CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
) COMMENT '用户角色关联表';

-- 角色-菜单关联表
CREATE TABLE sys_role_menu (
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, menu_id)
) COMMENT '角色菜单关联表';

-- 供应商资质审核表
CREATE TABLE sys_supplier_audit (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id           BIGINT       NOT NULL COMMENT '关联用户ID',
    enterprise_name   VARCHAR(200) NOT NULL,
    credit_code       VARCHAR(64),
    license_file_hash VARCHAR(128) COMMENT '营业执照SHA-256',
    license_ipfs_cid  VARCHAR(200) COMMENT '营业执照IPFS CID',
    cert_file_hash    VARCHAR(128) COMMENT '资质证书SHA-256',
    cert_ipfs_cid     VARCHAR(200) COMMENT '资质证书IPFS CID',
    audit_status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
    auditor_id        BIGINT       COMMENT '审核人ID',
    audit_opinion     VARCHAR(500),
    audit_time        DATETIME,
    tx_hash           VARCHAR(128) COMMENT '上链交易哈希',
    create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_audit_status (audit_status)
) COMMENT '供应商资质审核表';

-- 操作日志表
CREATE TABLE sys_operate_log (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id        BIGINT,
    username       VARCHAR(64),
    operation      VARCHAR(200) COMMENT '操作描述',
    method         VARCHAR(500) COMMENT '请求方法',
    params         TEXT         COMMENT '请求参数',
    ip             VARCHAR(64),
    result_status  TINYINT      COMMENT '0-失败 1-成功',
    error_msg      TEXT,
    tx_hash        VARCHAR(128) COMMENT '关联的链上交易哈希',
    operation_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_operation_time (operation_time)
) COMMENT '操作日志表';

-- ============================================================
-- 二、供应商管理模块
-- ============================================================

-- 设计文档表
CREATE TABLE bus_design_document (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    supplier_id      BIGINT       NOT NULL COMMENT '供应商用户ID',
    doc_name         VARCHAR(200) NOT NULL COMMENT '文档名称',
    doc_type         VARCHAR(50)  COMMENT '图纸/合规声明',
    version          VARCHAR(20)  COMMENT '版本号 v1.0',
    update_note      VARCHAR(500) COMMENT '更新说明',
    file_hash        VARCHAR(128) NOT NULL COMMENT 'SHA-256 哈希',
    ipfs_cid         VARCHAR(200) COMMENT 'IPFS CID',
    file_size        BIGINT       COMMENT '文件大小(字节)',
    file_name        VARCHAR(300) COMMENT '原始文件名',
    tx_hash          VARCHAR(128) COMMENT '上链交易哈希',
    chain_status     VARCHAR(20)  DEFAULT 'PENDING' COMMENT 'PENDING/ON_CHAIN',
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_supplier_id (supplier_id)
) COMMENT '设计文档表';

-- BOM 物料清单表
CREATE TABLE bus_bom (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    supplier_id      BIGINT       NOT NULL,
    bom_name         VARCHAR(200) NOT NULL COMMENT 'BOM名称',
    design_doc_id    BIGINT       COMMENT '关联设计文档ID',
    version          VARCHAR(20),
    file_hash        VARCHAR(128) COMMENT 'BOM文件SHA-256',
    ipfs_cid         VARCHAR(200),
    tx_hash          VARCHAR(128),
    chain_status     VARCHAR(20)  DEFAULT 'PENDING',
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_supplier_id (supplier_id)
) COMMENT 'BOM物料清单表';

-- BOM 明细项
CREATE TABLE bus_bom_item (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    bom_id        BIGINT       NOT NULL,
    part_name     VARCHAR(200) NOT NULL COMMENT '物料名称',
    part_number   VARCHAR(100) COMMENT '物料编号',
    specification VARCHAR(300) COMMENT '规格型号',
    quantity      INT          NOT NULL DEFAULT 1 COMMENT '数量',
    unit          VARCHAR(20)  COMMENT '单位',
    remark        VARCHAR(500),
    INDEX idx_bom_id (bom_id)
) COMMENT 'BOM明细项表';

-- 生产订单表 (ProductionRequest)
CREATE TABLE bus_production_request (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id            VARCHAR(64)  UNIQUE COMMENT '链上全局订单ID',
    supplier_id         BIGINT       NOT NULL COMMENT '供应商ID',
    bom_id              BIGINT       COMMENT '关联BOM',
    design_doc_id       BIGINT       COMMENT '关联设计文档',
    design_doc_hash     VARCHAR(128) COMMENT '设计文档哈希',
    quantity            INT          NOT NULL COMMENT '生产数量',
    expected_delivery   DATE         COMMENT '期望交期',
    quality_requirement TEXT         COMMENT '质量要求',
    target_manufacturer BIGINT       COMMENT '定向制造商ID(NULL为广播)',
    assembly_assembler_id BIGINT     NULL COMMENT '指定组装商用户ID(NULL不限制)',
    status              VARCHAR(30)  NOT NULL DEFAULT 'PENDING_ACCEPTANCE' COMMENT '订单状态',
    tx_hash             VARCHAR(128),
    create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_supplier_id (supplier_id),
    INDEX idx_status (status),
    INDEX idx_target_manufacturer (target_manufacturer),
    INDEX idx_pr_assembly_assembler (assembly_assembler_id)
) COMMENT '生产订单表';

-- ============================================================
-- 三、制造商管理模块
-- ============================================================

-- 制造协议表 (ManufacturingAgreement)
CREATE TABLE bus_manufacturing_agreement (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id          VARCHAR(64) NOT NULL COMMENT '关联订单ID',
    manufacturer_id   BIGINT      NOT NULL,
    final_price       DECIMAL(12,2) COMMENT '最终价格',
    delivery_date     DATE          COMMENT '承诺交货期',
    agreement_hash    VARCHAR(128) COMMENT '协议文件SHA-256',
    agreement_cid     VARCHAR(200) COMMENT '协议文件IPFS CID',
    manufacturer_sign VARCHAR(500) COMMENT '制造商数字签名',
    supplier_sign     VARCHAR(500) COMMENT '供应商数字签名',
    tx_hash           VARCHAR(128),
    create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order_id (order_id),
    INDEX idx_manufacturer_id (manufacturer_id)
) COMMENT '制造协议表';

-- 生产批次表
CREATE TABLE bus_production_batch (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id        VARCHAR(64)  NOT NULL UNIQUE COMMENT '批次号',
    order_id        VARCHAR(64)  NOT NULL COMMENT '关联订单ID',
    manufacturer_id BIGINT       NOT NULL,
    bom_item_id     BIGINT       NULL COMMENT 'BOM明细行(子件)，与订单数量×行用量对齐',
    planned_qty     INT          COMMENT '计划数量',
    completed_qty   INT          DEFAULT 0 COMMENT '已完成数量',
    status          VARCHAR(20)  DEFAULT 'CREATED' COMMENT 'CREATED/IN_PROGRESS/COMPLETED',
    tx_hash         VARCHAR(128) COMMENT '上链交易哈希',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_order_id (order_id),
    INDEX idx_manufacturer_id (manufacturer_id),
    INDEX idx_batch_order_bom_item (order_id, bom_item_id)
) COMMENT '生产批次表';

-- 设备记录表 (DeviceRecord / ECID)
CREATE TABLE bus_device_record (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    ecid              VARCHAR(100) NOT NULL UNIQUE COMMENT '物理唯一标识',
    order_id          VARCHAR(64)  NOT NULL,
    batch_id          VARCHAR(64)  NOT NULL,
    manufacturer_id   BIGINT       NOT NULL,
    bom_item_id       BIGINT       NULL COMMENT 'BOM明细行(子件)',
    device_type       VARCHAR(100) COMMENT '设备类型',
    manufacture_time  DATETIME     COMMENT '生产时间',
    status            VARCHAR(30)  NOT NULL DEFAULT 'PRODUCED' COMMENT 'PRODUCED/QC_PASS/REJECTED/ASSEMBLED/SOLD/DECOMMISSIONED',
    test_report_hash  VARCHAR(128) COMMENT '测试报告哈希',
    test_report_cid   VARCHAR(200) COMMENT '测试报告IPFS CID',
    tx_hash           VARCHAR(128) COMMENT '注册上链交易哈希',
    chain_registered  TINYINT      DEFAULT 0 COMMENT '是否已上链注册',
    released_to_assembler TINYINT NOT NULL DEFAULT 0 COMMENT '制造商已放行给组装商(1)后方可领用',
    create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_batch_id (batch_id),
    INDEX idx_manufacturer_id (manufacturer_id),
    INDEX idx_status (status),
    INDEX idx_device_released (released_to_assembler),
    INDEX idx_device_order_bom_item (order_id, bom_item_id)
) COMMENT '设备记录表(ECID)';

-- 质检报告表
CREATE TABLE bus_quality_report (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    report_type     VARCHAR(20)  NOT NULL COMMENT 'MANUFACTURE/ASSEMBLY/INSPECTION',
    target_type     VARCHAR(20)  NOT NULL COMMENT 'ECID/BATCH/SN',
    target_id       VARCHAR(100) NOT NULL COMMENT '目标标识(ECID/批次号/SN)',
    reporter_id     BIGINT       NOT NULL COMMENT '上传者ID',
    report_name     VARCHAR(200),
    file_hash       VARCHAR(128),
    ipfs_cid        VARCHAR(200),
    result          VARCHAR(20)  COMMENT 'PASS/FAIL',
    remark          VARCHAR(500),
    signer_addr     VARCHAR(128) COMMENT '签名者区块链地址',
    signature       TEXT         COMMENT '数字签名',
    tx_hash         VARCHAR(128),
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_target (target_type, target_id),
    INDEX idx_reporter_id (reporter_id)
) COMMENT '质检报告表';

-- 不合格记录表 (RejectRecord)
CREATE TABLE bus_reject_record (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    ecid            VARCHAR(100) NOT NULL,
    batch_id        VARCHAR(64),
    manufacturer_id BIGINT       NOT NULL,
    order_id        VARCHAR(64)  COMMENT '关联生产订单，供应商处置列表用',
    reason          VARCHAR(500) COMMENT '不合格原因',
    disposal_type   VARCHAR(30)  COMMENT 'RETURN退货 / DESTROY销毁',
    disposal_status VARCHAR(30)  DEFAULT 'PENDING' COMMENT 'AWAITING_SUPPLIER/AWAITING_MFG_DESTROY/COMPLETED',
    tx_hash         VARCHAR(128) COMMENT 'MFG_REJECT 锚定',
    disposal_complete_tx_hash VARCHAR(128) COMMENT '处置完结锚定',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_ecid (ecid),
    INDEX idx_batch_id (batch_id),
    INDEX idx_order_id_reject (order_id)
) COMMENT '不合格记录表';

-- ============================================================
-- 四、组装商管理模块
-- ============================================================

-- 组装批次表
CREATE TABLE bus_assembly_batch (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_no      VARCHAR(64)  NOT NULL UNIQUE COMMENT '组装批次号',
    assembler_id  BIGINT       NOT NULL,
    order_id      VARCHAR(64)  NULL COMMENT '关联生产订单业务号',
    product_model VARCHAR(200) COMMENT '产品型号',
    planned_qty   INT,
    completed_qty INT          DEFAULT 0,
    status        VARCHAR(20)  DEFAULT 'CREATED',
    tx_hash       VARCHAR(128) COMMENT '上链交易哈希',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_assembler_id (assembler_id),
    INDEX idx_assembly_batch_order (order_id)
) COMMENT '组装批次表';

-- 组装记录表 (整机SN - ECID映射)
CREATE TABLE bus_assembly_record (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    sn                VARCHAR(100) NOT NULL UNIQUE COMMENT '整机序列号',
    assembly_batch_no VARCHAR(64)  NOT NULL COMMENT '组装批次号',
    assembler_id      BIGINT       NOT NULL,
    current_holder_id BIGINT       COMMENT '当前货权用户ID(组装完成后默认为组装商，收货后更新)',
    ecid_list         JSON         COMMENT '部件ECID列表 ["ecid1","ecid2"]',
    firmware_version  VARCHAR(100) COMMENT '固件/系统版本',
    test_report_hash  VARCHAR(128),
    test_report_cid   VARCHAR(200),
    test_result       VARCHAR(20)  COMMENT 'PASS/FAIL',
    assembler_sign    VARCHAR(500) COMMENT '组装商签名',
    status            VARCHAR(30)  DEFAULT 'ASSEMBLED' COMMENT 'ASSEMBLED/ON_CHAIN/IN_STOCK/IN_TRANSIT/SOLD/RECALLING/DECOMMISSIONED',
    tx_hash           VARCHAR(128),
    assembly_tx_hash  VARCHAR(128) COMMENT '组装创建上链哈希',
    chain_registered  TINYINT      DEFAULT 0,
    assembly_time     DATETIME,
    create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_assembly_batch (assembly_batch_no),
    INDEX idx_assembler_id (assembler_id),
    INDEX idx_status (status)
) COMMENT '组装记录表(整机SN)';

-- ============================================================
-- 五、分销与流通模块
-- ============================================================

-- 物流流转事件表 (TransferEvent)
CREATE TABLE bus_transfer_event (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    sn               VARCHAR(100) COMMENT '产品SN',
    batch_no         VARCHAR(64)  COMMENT '批次号(批量流转)',
    tracking_number  VARCHAR(100) COMMENT '物流单号',
    logistics_company VARCHAR(100) COMMENT '物流公司',
    sender_id        BIGINT       NOT NULL COMMENT '发送方ID',
    receiver_id      BIGINT       NOT NULL COMMENT '接收方ID',
    transfer_type    VARCHAR(20)  NOT NULL COMMENT 'SHIP/RECEIVE/RETURN',
    ship_time        DATETIME,
    estimated_arrival DATETIME,
    actual_arrival   DATETIME,
    tx_hash          VARCHAR(128),
    receive_tx_hash  VARCHAR(128) COMMENT '收货上链交易哈希',
    status           VARCHAR(20)  DEFAULT 'PENDING' COMMENT 'PENDING/IN_TRANSIT/RECEIVED/ANOMALY',
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sn (sn),
    INDEX idx_sender (sender_id),
    INDEX idx_receiver (receiver_id),
    INDEX idx_tracking (tracking_number)
) COMMENT '物流流转事件表';

-- 销售记录表
CREATE TABLE bus_sales_record (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
    sn                    VARCHAR(100) NOT NULL COMMENT '产品SN',
    seller_id             BIGINT       NOT NULL COMMENT '销售门店/分销商ID',
    sale_time             DATETIME     NOT NULL,
    customer_hash         VARCHAR(128) COMMENT '客户身份哈希(隐私)',
    customer_name_enc     VARCHAR(300) COMMENT '加密客户姓名(仅本地)',
    customer_phone_enc    VARCHAR(300) COMMENT '加密客户手机(仅本地)',
    customer_anonymous    TINYINT      NOT NULL DEFAULT 0 COMMENT '1=匿名销售',
    customer_segment      VARCHAR(16)  COMMENT 'B2B/B2C',
    invoice_hash          VARCHAR(128) COMMENT '销售凭证哈希',
    invoice_cid           VARCHAR(200) COMMENT '销售凭证IPFS CID',
    tx_hash               VARCHAR(128),
    create_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_sn (sn),
    INDEX idx_seller (seller_id),
    INDEX idx_customer_hash (customer_hash)
) COMMENT '销售记录表';

-- ============================================================
-- 六、终端用户与售后模块
-- ============================================================

-- 用户产品绑定表
CREATE TABLE bus_user_product (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    sn            VARCHAR(100) NOT NULL,
    bind_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verify_status VARCHAR(20)  DEFAULT 'PENDING' COMMENT 'PENDING/VERIFIED',
    tx_hash       VARCHAR(128) COMMENT '绑定上链交易哈希',
    UNIQUE KEY uk_user_sn (user_id, sn),
    INDEX idx_sn (sn)
) COMMENT '用户产品绑定表';

-- 投诉/召回请求表 (RecallRequest)
CREATE TABLE bus_recall_request (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_no      VARCHAR(64)  NOT NULL UNIQUE COMMENT '投诉单号',
    user_id         BIGINT       NOT NULL,
    sn              VARCHAR(100) NOT NULL COMMENT '问题产品SN',
    fault_type      VARCHAR(50)  COMMENT '故障类型',
    fault_desc      TEXT         COMMENT '故障描述',
    evidence_urls   JSON         COMMENT '证据文件URL列表',
    status          VARCHAR(20)  DEFAULT 'SUBMITTED' COMMENT 'SUBMITTED/PROCESSING/RESOLVED/CLOSED',
    affected_sns    JSON         COMMENT '受影响的SN列表(系统计算)',
    tx_hash         VARCHAR(128),
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_sn (sn),
    INDEX idx_status (status)
) COMMENT '投诉召回请求表';

-- 报废登记表 (Decommission)
CREATE TABLE bus_decommission (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    sn                VARCHAR(100) NOT NULL,
    applicant_id      BIGINT       COMMENT '申请人ID',
    recycler_id       BIGINT       COMMENT '回收机构ID',
    disposal_method   VARCHAR(100) COMMENT '处置方式',
    disposal_time     DATETIME,
    recycler_name     VARCHAR(200),
    tx_hash           VARCHAR(128),
    status            VARCHAR(20)  DEFAULT 'APPLIED' COMMENT 'APPLIED/RECYCLED/COMPLETED',
    create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sn (sn)
) COMMENT '报废登记表';

-- ============================================================
-- 七、监管与控制台模块
-- ============================================================

-- 抽检任务表
CREATE TABLE bus_inspection_task (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_no         VARCHAR(64)  NOT NULL UNIQUE COMMENT '抽检任务号',
    inspector_id    BIGINT       NOT NULL COMMENT '质检机构ID',
    target_type     VARCHAR(20)  NOT NULL COMMENT 'ECID/BATCH/SN',
    target_id       VARCHAR(100) NOT NULL,
    inspection_result VARCHAR(20) COMMENT 'PASS/FAIL',
    report_hash     VARCHAR(128),
    report_cid      VARCHAR(200),
    inspector_sign  VARCHAR(500) COMMENT '质检机构签名',
    tx_hash         VARCHAR(128),
    status          VARCHAR(20)  DEFAULT 'CREATED' COMMENT 'CREATED/IN_PROGRESS/COMPLETED',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_inspector (inspector_id),
    INDEX idx_target (target_type, target_id)
) COMMENT '抽检任务表';

-- 召回通告表
CREATE TABLE bus_recall_notice (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    notice_no        VARCHAR(64)  NOT NULL UNIQUE COMMENT '召回通告号',
    issuer_id        BIGINT       NOT NULL COMMENT '发布者(监管机构)ID',
    fault_source_sn  VARCHAR(100) COMMENT '问题源SN',
    fault_batch_id   VARCHAR(64)  COMMENT '问题批次',
    fault_ecid       VARCHAR(100) COMMENT '问题部件ECID',
    affected_sns     JSON         COMMENT '受影响SN列表',
    disposal_plan    TEXT         COMMENT '处理方案',
    status           VARCHAR(20)  DEFAULT 'PUBLISHED' COMMENT 'PUBLISHED/IN_PROGRESS/COMPLETED',
    tx_hash          VARCHAR(128),
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_issuer (issuer_id)
) COMMENT '召回通告表';

-- IPFS 文件记录表(统一管理)
CREATE TABLE bus_ipfs_file (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    file_name   VARCHAR(300) NOT NULL,
    file_hash   VARCHAR(128) NOT NULL COMMENT 'SHA-256',
    ipfs_cid    VARCHAR(200) NOT NULL,
    file_size   BIGINT,
    file_type   VARCHAR(100) COMMENT 'MIME类型',
    uploader_id BIGINT       NOT NULL,
    biz_type    VARCHAR(50)  COMMENT '业务类型 DESIGN/BOM/AGREEMENT/REPORT/CERTIFICATE/INVOICE',
    biz_id      BIGINT       COMMENT '业务表ID',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_uploader (uploader_id),
    INDEX idx_biz (biz_type, biz_id),
    INDEX idx_file_hash (file_hash)
) COMMENT 'IPFS文件记录表';

-- ============================================================
-- 初始化数据
-- ============================================================

-- 初始化角色
INSERT INTO sys_role (role_key, role_name, sort_order) VALUES
('admin',        '系统管理员',    0),
('supplier',     '供应商',        1),
('manufacturer', '制造商',        2),
('assembler',    '组装商',        3),
('distributor',  '分销商',        4),
('regulator',    '监管/质检机构',  5),
('enduser',      '终端用户',      6);

-- 初始化管理员账号 (密码: admin123 BCrypt加密)
INSERT INTO sys_user (username, password, enterprise_name, status) VALUES
('admin', '$2a$10$EqKcp1WFKVQISheBxnGRhO7VlCNBEkMsEOoKbGSifGbSMEr3lsvCW', '系统管理员', 1);

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.role_key = 'admin'
WHERE u.username = 'admin'
LIMIT 1;

-- 初始化菜单
INSERT INTO sys_menu (parent_id, menu_name, path, component, perms, menu_type, icon, sort_order) VALUES
-- 系统管理
(0, '系统管理', '/system', NULL, NULL, 'M', 'Setting', 1),
(1, '用户管理', '/system/user', 'system/user/index', 'system:user:list', 'C', 'User', 1),
(1, '角色管理', '/system/role', 'system/role/index', 'system:role:list', 'C', 'UserFilled', 2),
(1, '菜单管理', '/system/menu', 'system/menu/index', 'system:menu:list', 'C', 'Menu', 3),
(1, '操作日志', '/system/log', 'system/log/index', 'system:log:list', 'C', 'Document', 4),

-- 供应商管理
(0, '供应商管理', '/supplier', NULL, NULL, 'M', 'Shop', 2),
(6, '设计文档', '/supplier/design', 'supplier/design/index', 'supplier:design:list', 'C', 'Document', 1),
(6, 'BOM管理',  '/supplier/bom', 'supplier/bom/index', 'supplier:bom:list', 'C', 'List', 2),
(6, '生产订单', '/supplier/order', 'supplier/order/index', 'supplier:order:list', 'C', 'Tickets', 3),
(6, '进度跟踪', '/supplier/track', 'supplier/track/index', 'supplier:track:list', 'C', 'DataLine', 4),

-- 制造商管理
(0, '制造商管理', '/manufacturer', NULL, NULL, 'M', 'OfficeBuilding', 3),
(11, '订单接收', '/manufacturer/order', 'manufacturer/order/index', 'manufacturer:order:list', 'C', 'Tickets', 1),
(11, '生产管理', '/manufacturer/production', 'manufacturer/production/index', 'manufacturer:production:list', 'C', 'Cpu', 2),
(11, '质检管理', '/manufacturer/quality', 'manufacturer/quality/index', 'manufacturer:quality:list', 'C', 'Checked', 3),
(11, '数据看板', '/manufacturer/dashboard', 'manufacturer/dashboard/index', 'manufacturer:dashboard:view', 'C', 'DataAnalysis', 4),

-- 组装商管理
(0, '组装商管理', '/assembler', NULL, NULL, 'M', 'SetUp', 4),
(16, '部件入库', '/assembler/intake', 'assembler/intake/index', 'assembler:intake:list', 'C', 'Box', 1),
(16, '组装管理', '/assembler/assembly', 'assembler/assembly/index', 'assembler:assembly:list', 'C', 'Connection', 2),
(16, '整机质检', '/assembler/quality', 'assembler/quality/index', 'assembler:quality:list', 'C', 'Checked', 3),
(16, '数据看板', '/assembler/dashboard', 'assembler/dashboard/index', 'assembler:dashboard:view', 'C', 'DataAnalysis', 4),

-- 分销管理
(0, '分销管理', '/distributor', NULL, NULL, 'M', 'Van', 5),
(21, '物流流转', '/distributor/logistics', 'distributor/logistics/index', 'distributor:logistics:list', 'C', 'Ship', 1),
(21, '库存管理', '/distributor/inventory', 'distributor/inventory/index', 'distributor:inventory:list', 'C', 'GoodsFilled', 2),
(21, '销售记录', '/distributor/sales', 'distributor/sales/index', 'distributor:sales:list', 'C', 'Sell', 3),

-- 终端用户
(0, '终端用户', '/enduser', NULL, NULL, 'M', 'UserFilled', 6),
(25, '溯源查询', '/enduser/trace', 'enduser/trace/index', 'enduser:trace:query', 'C', 'Search', 1),
(25, '投诉反馈', '/enduser/complaint', 'enduser/complaint/index', 'enduser:complaint:list', 'C', 'ChatDotRound', 2),
(25, '报废登记', '/enduser/decommission', 'enduser/decommission/index', 'enduser:decommission:list', 'C', 'Delete', 3),

-- 监管控制台
(0, '监管控制台', '/regulator', NULL, NULL, 'M', 'Monitor', 7),
(29, '资质审核', '/regulator/audit', 'regulator/audit/index', 'regulator:audit:list', 'C', 'Stamp', 1),
(29, '抽检任务', '/regulator/inspection', 'regulator/inspection/index', 'regulator:inspection:list', 'C', 'FirstAidKit', 2),
(29, '召回管理', '/regulator/recall', 'regulator/recall/index', 'regulator:recall:list', 'C', 'WarningFilled', 3),
(29, '审计日志', '/regulator/log', 'regulator/log/index', 'regulator:log:list', 'C', 'Notebook', 4);

-- 角色-菜单分配
-- 系统管理员: 全部菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m
WHERE r.role_key = 'admin';

-- 供应商: 系统管理(部分) + 供应商管理
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN (1,2,5,6,7,8,9,10)
WHERE r.role_key = 'supplier';

-- 制造商: 系统管理(部分) + 制造商管理
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN (1,2,5,11,12,13,14,15)
WHERE r.role_key = 'manufacturer';

-- 组装商
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN (1,2,5,16,17,18,19,20)
WHERE r.role_key = 'assembler';

-- 分销商
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN (1,2,5,21,22,23,24)
WHERE r.role_key = 'distributor';

-- 监管机构: 全部菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m
WHERE r.role_key = 'regulator';

-- 终端用户
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN (25,26,27,28)
WHERE r.role_key = 'enduser';

-- 扩展：产品绑定、串货监控（亦见 db/patch-menus-feature-extension.sql，已建库可单独重复执行）
INSERT INTO sys_menu (parent_id, menu_name, path, component, perms, menu_type, icon, sort_order)
SELECT id, '产品绑定', '/enduser/bind', 'enduser/bind/index', 'enduser:bind:list', 'C', 'Link', 2
FROM sys_menu
WHERE path = '/enduser' AND parent_id = 0 AND menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/enduser/bind')
LIMIT 1;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.path = '/enduser/bind'
WHERE r.role_key = 'enduser'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

INSERT INTO sys_menu (parent_id, menu_name, path, component, perms, menu_type, icon, sort_order)
SELECT id, '串货监控', '/regulator/anomaly', 'regulator/anomaly/index', 'regulator:anomaly:list', 'C', 'Histogram', 3
FROM sys_menu
WHERE path = '/regulator' AND parent_id = 0 AND menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/regulator/anomaly')
LIMIT 1;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.path = '/regulator/anomaly'
WHERE r.role_key = 'regulator'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- 不合格处置（制造商质检不合格 → 退货/销毁闭环）
INSERT INTO sys_menu (parent_id, menu_name, path, component, perms, menu_type, icon, sort_order)
SELECT id, '不合格处置', '/supplier/reject', 'supplier/reject/index', 'supplier:reject:list', 'C', 'Warning', 5
FROM sys_menu
WHERE path = '/supplier' AND parent_id = 0 AND menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/supplier/reject')
LIMIT 1;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.path = '/supplier/reject'
WHERE r.role_key IN ('admin', 'supplier')
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
