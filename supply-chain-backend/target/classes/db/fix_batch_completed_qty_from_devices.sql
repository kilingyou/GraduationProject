-- 历史数据：未完工批次按「本批 ECID 条数」回填完成数量（不超过计划数量）；已完工批次按实际设备数对齐
UPDATE bus_production_batch b
INNER JOIN (
    SELECT batch_id, COUNT(*) AS cnt
    FROM bus_device_record
    GROUP BY batch_id
) d ON b.batch_id = d.batch_id
SET b.completed_qty = CASE
    WHEN b.status = 'COMPLETED' THEN d.cnt
    WHEN b.planned_qty IS NOT NULL AND b.planned_qty > 0 THEN LEAST(b.planned_qty, d.cnt)
    ELSE d.cnt
END
WHERE d.cnt > 0;
