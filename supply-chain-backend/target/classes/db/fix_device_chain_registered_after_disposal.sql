-- 历史数据修复：已完成退货/销毁处置（RejectRecord COMPLETED 且有 disposal_complete_tx_hash）
-- 对应 ECID 在 bus_device_record 中补 chain_registered=1；tx_hash 为空时写入处置完结哈希。
-- 要求表 bus_reject_record 已含 disposal_complete_tx_hash 列（见 alter_reject_disposition / schema.sql）

UPDATE bus_device_record d
INNER JOIN (
    SELECT r.ecid,
           r.manufacturer_id,
           r.disposal_complete_tx_hash
    FROM bus_reject_record r
    INNER JOIN (
        SELECT ecid, manufacturer_id, MAX(id) AS max_id
        FROM bus_reject_record
        WHERE disposal_status = 'COMPLETED'
          AND disposal_complete_tx_hash IS NOT NULL
          AND disposal_complete_tx_hash <> ''
        GROUP BY ecid, manufacturer_id
    ) t ON r.id = t.max_id
) x ON d.ecid = x.ecid AND d.manufacturer_id = x.manufacturer_id
SET d.chain_registered = 1,
    d.tx_hash = CASE
        WHEN d.tx_hash IS NULL OR d.tx_hash = '' THEN x.disposal_complete_tx_hash
        ELSE d.tx_hash
    END;
