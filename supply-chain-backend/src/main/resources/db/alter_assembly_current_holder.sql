-- 已有库升级：执行一次即可（若列已存在会报错，可忽略）
ALTER TABLE bus_assembly_record
    ADD COLUMN current_holder_id BIGINT NULL COMMENT '当前货权用户ID' AFTER assembler_id;

UPDATE bus_assembly_record
SET current_holder_id = assembler_id
WHERE current_holder_id IS NULL;
