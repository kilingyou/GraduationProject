-- 侧栏平铺：删除「供应商管理」目录行，将其子菜单提升为顶级（parent_id=0）
-- UTF-8 连接执行；执行前请备份。

SET @sup_root := (
  SELECT id FROM sys_menu
  WHERE path = '/supplier' AND menu_type = 'M' AND (parent_id = 0 OR parent_id IS NULL)
  LIMIT 1
);

UPDATE sys_menu SET parent_id = 0 WHERE @sup_root IS NOT NULL AND parent_id = @sup_root;

DELETE FROM sys_role_menu WHERE @sup_root IS NOT NULL AND menu_id = @sup_root;
DELETE FROM sys_menu WHERE @sup_root IS NOT NULL AND id = @sup_root;
