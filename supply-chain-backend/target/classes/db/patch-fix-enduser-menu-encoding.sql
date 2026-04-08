-- 修复终端用户子菜单中文乱码（常见于 Windows 下用默认编码执行 UTF-8 脚本导致「产品绑定」等写入错误）
-- 请在 UTF-8 连接下执行：mysql --default-character-set=utf8mb4 ...

UPDATE sys_menu SET menu_name = '终端用户' WHERE path = '/enduser' AND menu_type = 'M';
UPDATE sys_menu SET menu_name = '溯源查询' WHERE path = '/enduser/trace' AND menu_type = 'C';
UPDATE sys_menu SET menu_name = '产品绑定' WHERE path = '/enduser/bind' AND menu_type = 'C';
UPDATE sys_menu SET menu_name = '投诉反馈' WHERE path = '/enduser/complaint' AND menu_type = 'C';
UPDATE sys_menu SET menu_name = '报废登记' WHERE path = '/enduser/decommission' AND menu_type = 'C';
