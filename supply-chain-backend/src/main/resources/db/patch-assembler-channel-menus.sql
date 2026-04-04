-- 组装商侧：「渠道流通」二级菜单（含物流/库存/销售），与前端 /assembler/circulation/* 对齐
-- 在已存在 sys_menu 的库上执行；可重复运行（按 path 判重）

-- 目录：渠道流通（父 = 组装商管理）
INSERT INTO sys_menu (parent_id, menu_name, path, component, perms, menu_type, icon, sort_order)
SELECT id, '渠道流通', '/assembler/circulation', 'assembler/circulation/ParentView', 'assembler:channel:view', 'M', 'Van', 4
FROM sys_menu
WHERE path = '/assembler' AND parent_id = 0 AND menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/assembler/circulation')
LIMIT 1;

INSERT INTO sys_menu (parent_id, menu_name, path, component, perms, menu_type, icon, sort_order)
SELECT m.id, '物流流转', '/assembler/circulation/logistics', 'distributor/logistics/index', 'assembler:channel:logistics', 'C', 'Ship', 1
FROM sys_menu m
WHERE m.path = '/assembler/circulation'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/assembler/circulation/logistics')
LIMIT 1;

INSERT INTO sys_menu (parent_id, menu_name, path, component, perms, menu_type, icon, sort_order)
SELECT m.id, '库存管理', '/assembler/circulation/inventory', 'distributor/inventory/index', 'assembler:channel:inventory', 'C', 'GoodsFilled', 2
FROM sys_menu m
WHERE m.path = '/assembler/circulation'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/assembler/circulation/inventory')
LIMIT 1;

INSERT INTO sys_menu (parent_id, menu_name, path, component, perms, menu_type, icon, sort_order)
SELECT m.id, '销售记录', '/assembler/circulation/sales', 'distributor/sales/index', 'assembler:channel:sales', 'C', 'Sell', 3
FROM sys_menu m
WHERE m.path = '/assembler/circulation'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/assembler/circulation/sales')
LIMIT 1;

-- 组装商角色授权（含目录 + 三个子页）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.path IN (
    '/assembler/circulation',
    '/assembler/circulation/logistics',
    '/assembler/circulation/inventory',
    '/assembler/circulation/sales'
)
WHERE r.role_key = 'assembler'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- 管理员同步可见（若库非「全量菜单」初始化）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.path IN (
    '/assembler/circulation',
    '/assembler/circulation/logistics',
    '/assembler/circulation/inventory',
    '/assembler/circulation/sales'
)
WHERE r.role_key = 'admin'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
