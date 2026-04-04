-- 销售记录：客户类型与匿名销售（可重复执行）
SET @db := DATABASE();
SET @exists_anon := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'bus_sales_record' AND COLUMN_NAME = 'customer_anonymous'
);
SET @sql_anon := IF(@exists_anon = 0,
    'ALTER TABLE bus_sales_record ADD COLUMN customer_anonymous TINYINT NOT NULL DEFAULT 0 COMMENT ''1=匿名销售，链上仅存摘要'' AFTER customer_phone_enc',
    'SELECT 1');
PREPARE s1 FROM @sql_anon;
EXECUTE s1;
DEALLOCATE PREPARE s1;

SET @exists_seg := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'bus_sales_record' AND COLUMN_NAME = 'customer_segment'
);
SET @sql_seg := IF(@exists_seg = 0,
    'ALTER TABLE bus_sales_record ADD COLUMN customer_segment VARCHAR(16) NULL COMMENT ''B2B/B2C'' AFTER customer_anonymous',
    'SELECT 1');
PREPARE s2 FROM @sql_seg;
EXECUTE s2;
DEALLOCATE PREPARE s2;
