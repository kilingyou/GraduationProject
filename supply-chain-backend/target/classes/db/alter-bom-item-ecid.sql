-- 子件 ECID：生产批次、设备记录绑定 BOM 明细行（执行一次即可）
ALTER TABLE bus_production_batch
    ADD COLUMN bom_item_id BIGINT NULL COMMENT 'BOM明细行ID(子件)' AFTER manufacturer_id;
ALTER TABLE bus_device_record
    ADD COLUMN bom_item_id BIGINT NULL COMMENT 'BOM明细行ID(子件)' AFTER manufacturer_id;

CREATE INDEX idx_batch_order_bom_item ON bus_production_batch (order_id, bom_item_id);
CREATE INDEX idx_device_order_bom_item ON bus_device_record (order_id, bom_item_id);
