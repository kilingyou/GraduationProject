-- 修复「不合格处置」菜单名被存成 ?????（PowerShell 执行 INSERT 时编码错误）
-- 任意客户端均可执行；中文用 UTF-8 字节避免乱码
UPDATE sys_menu
SET menu_name = CONVERT(UNHEX('E4B88DE59088E6A0BCE5A484E7BDAE') USING utf8mb4)
WHERE path = '/supplier/reject';
