-- 组装批次绑定生产订单；部件需制造商「放行给组装商」后组装商方可领用
-- 执行前请备份；已有数据：部件默认视为已放行，避免升级后无法组装

ALTER TABLE bus_assembly_batch
    ADD COLUMN order_id VARCHAR(64) NULL COMMENT '关联生产订单业务号' AFTER assembler_id,
    ADD INDEX idx_assembly_batch_order (order_id);

ALTER TABLE bus_device_record
    ADD COLUMN released_to_assembler TINYINT NOT NULL DEFAULT 0
        COMMENT '制造商已放行/发运给组装商(1)后方可被组装商领用' AFTER chain_registered,
    ADD INDEX idx_device_released (released_to_assembler);

UPDATE bus_device_record SET released_to_assembler = 1 WHERE released_to_assembler = 0;
