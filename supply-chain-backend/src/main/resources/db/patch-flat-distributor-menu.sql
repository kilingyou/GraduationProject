-- 侧栏平铺：删除「分销管理」目录行，将其子菜单提升为顶级（parent_id=0）
-- UTF-8 连接执行；执行前请备份。

SET @dist_root := (
  SELECT id FROM sys_menu
  WHERE path = '/distributor' AND menu_type = 'M' AND (parent_id = 0 OR parent_id IS NULL)
  LIMIT 1
);

UPDATE sys_menu SET parent_id = 0 WHERE @dist_root IS NOT NULL AND parent_id = @dist_root;

DELETE FROM sys_role_menu WHERE @dist_root IS NOT NULL AND menu_id = @dist_root;
DELETE FROM sys_menu WHERE @dist_root IS NOT NULL AND id = @dist_root;
