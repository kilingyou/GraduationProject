-- 已有库升级：可重复执行；列已存在时不会报错
-- 修复：Unknown column 'current_holder_id' in 'field list'（组装记录插入失败）

SET @db := DATABASE();
SET @exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db
      AND TABLE_NAME = 'bus_assembly_record'
      AND COLUMN_NAME = 'current_holder_id'
);
SET @sql := IF(@exists = 0,
    'ALTER TABLE bus_assembly_record ADD COLUMN current_holder_id BIGINT NULL COMMENT ''当前货权用户ID(组装完成后默认为组装商，收货后更新)'' AFTER assembler_id',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE bus_assembly_record
SET current_holder_id = assembler_id
WHERE current_holder_id IS NULL;
