-- 已建库可单独执行：整机质检流程已并入「组装管理」创建记录，隐藏侧栏独立菜单（可重复执行）
UPDATE sys_menu SET visible = 0 WHERE path = '/assembler/quality';
