-- 生产订单指定组装商：仅该组装商可领用该单下部件 ECID（NULL 表示不限制，兼容旧数据）
ALTER TABLE bus_production_request
    ADD COLUMN assembly_assembler_id BIGINT NULL COMMENT '指定组装商用户ID' AFTER target_manufacturer;
CREATE INDEX idx_pr_assembly_assembler ON bus_production_request (assembly_assembler_id);
