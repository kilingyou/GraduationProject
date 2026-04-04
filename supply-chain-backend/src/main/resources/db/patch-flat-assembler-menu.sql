-- 侧栏与前端一致：删除「组装商管理」目录行，将其子菜单提升为顶级（parent_id=0）
-- 「渠道流通」仍为目录，其子页不变。执行前请备份；需 UTF-8 连接。
-- 若 admin 在「菜单管理」中依赖该父节点，删除后请在界面中核对树结构。

SET @asm_root := (
  SELECT id FROM sys_menu
  WHERE path = '/assembler' AND menu_type = 'M' AND (parent_id = 0 OR parent_id IS NULL)
  LIMIT 1
);

UPDATE sys_menu SET parent_id = 0 WHERE @asm_root IS NOT NULL AND parent_id = @asm_root;

DELETE FROM sys_role_menu WHERE @asm_root IS NOT NULL AND menu_id = @asm_root;
DELETE FROM sys_menu WHERE @asm_root IS NOT NULL AND id = @asm_root;
