USE supply_chain_db;

-- 1) Add standalone admin role if missing
INSERT INTO sys_role (role_key, role_name, sort_order, status)
SELECT 'admin', 'Admin', 0, 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role WHERE role_key = 'admin'
);

-- 2) Ensure built-in admin account is bound to admin role
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.role_key = 'admin'
WHERE u.username = 'admin'
  AND NOT EXISTS (
      SELECT 1 FROM sys_user_role ur
      WHERE ur.user_id = u.id AND ur.role_id = r.id
  );

-- 3) Remove old regulator binding from built-in admin account
DELETE ur
FROM sys_user_role ur
JOIN sys_user u ON u.id = ur.user_id
JOIN sys_role r ON r.id = ur.role_id
WHERE u.username = 'admin'
  AND r.role_key = 'regulator';

-- 4) Grant all menus to admin role
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m
WHERE r.role_key = 'admin'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm
      WHERE rm.role_id = r.id AND rm.menu_id = m.id
  );
