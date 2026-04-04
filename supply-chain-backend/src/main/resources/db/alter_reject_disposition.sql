-- 已有库升级：不合格记录扩展字段（全新库请忽略，以 schema.sql 为准）
-- 在 MySQL 客户端中按需执行；若列已存在会报错，可跳过对应语句。

ALTER TABLE bus_reject_record
    ADD COLUMN order_id VARCHAR(64) NULL COMMENT '关联生产订单' AFTER manufacturer_id;

ALTER TABLE bus_reject_record
    ADD COLUMN disposal_complete_tx_hash VARCHAR(128) NULL COMMENT '处置完结锚定' AFTER tx_hash;

CREATE INDEX idx_order_id_reject ON bus_reject_record (order_id);

-- 菜单与权限（与 schema.sql 末尾片段等效，可重复执行时先检查 sys_menu 是否已有 path=/supplier/reject）
