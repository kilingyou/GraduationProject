-- 在已执行 schema.sql 建库后附加执行：终端用户「产品绑定」、监管「串货监控」菜单与权限
-- 可重复执行（存在则跳过）

INSERT INTO sys_menu (parent_id, menu_name, path, component, perms, menu_type, icon, sort_order)
SELECT id, '产品绑定', '/enduser/bind', 'enduser/bind/index', 'enduser:bind:list', 'C', 'Link', 2
FROM sys_menu
WHERE path = '/enduser' AND parent_id = 0 AND menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/enduser/bind')
LIMIT 1;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.path = '/enduser/bind'
WHERE r.role_key = 'enduser'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id
  );

INSERT INTO sys_menu (parent_id, menu_name, path, component, perms, menu_type, icon, sort_order)
SELECT id, '串货监控', '/regulator/anomaly', 'regulator/anomaly/index', 'regulator:anomaly:list', 'C', 'Histogram', 3
FROM sys_menu
WHERE path = '/regulator' AND parent_id = 0 AND menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/regulator/anomaly')
LIMIT 1;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.path = '/regulator/anomaly'
WHERE r.role_key = 'regulator'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id
  );
