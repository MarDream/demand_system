-- 移除"通知管理"按钮权限（button:notification:manage）：
-- 通知中心"全部已读"不再做权限控制，所有登录用户均可一键全读。
-- 1) 清理各角色对该按钮权限的授权映射
-- 2) 删除权限字典中的按钮权限（sys_permissions.id=107）
-- 3) 删除角色管理权限树中挂在"系统配置"目录下的按钮节点（sys_menus.id=204）

START TRANSACTION;

DELETE rp FROM sys_role_permissions rp
JOIN sys_permissions p ON p.id = rp.permission_id
WHERE p.code = 'button:notification:manage';

DELETE FROM sys_permissions WHERE code = 'button:notification:manage';

DELETE FROM sys_menus WHERE permission_code = 'button:notification:manage';

COMMIT;

-- 验证（应均无结果）：
-- SELECT * FROM sys_permissions WHERE code = 'button:notification:manage';
-- SELECT * FROM sys_menus WHERE permission_code = 'button:notification:manage';
