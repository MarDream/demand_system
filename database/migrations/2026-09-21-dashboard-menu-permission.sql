-- 仪表盘菜单权限控制：
-- 1) 新增 menu:dashboard 权限字典
-- 2) 仪表盘菜单（sys_menus.id=1）挂上该权限码（原来为 NULL，所有人可见）
-- 3) 存量授权：所有当前至少拥有一个 menu:% 权限的角色补授 menu:dashboard，
--    保证已有角色的可见范围不变（此后新角色按「角色管理→权限范围」自行勾选）
-- 4) 无 menu:dashboard 的用户登录后，前端按菜单顺序落到第一个有权限的菜单（见 resolveHomePath）

START TRANSACTION;

INSERT INTO sys_permissions (code, name, type, description, status)
SELECT 'menu:dashboard', '仪表盘菜单', 'MENU', '仪表盘一级菜单入口', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permissions WHERE code = 'menu:dashboard');

UPDATE sys_menus SET permission_code = 'menu:dashboard' WHERE id = 1 AND (permission_code IS NULL OR permission_code = '');

INSERT INTO sys_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN sys_permissions p ON p.code = 'menu:dashboard'
WHERE EXISTS (
    SELECT 1 FROM sys_role_permissions rp
    JOIN sys_permissions pp ON pp.id = rp.permission_id
    WHERE rp.role_id = r.id AND pp.code LIKE 'menu:%'
  )
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permissions rp2
    JOIN sys_permissions pp2 ON pp2.id = rp2.permission_id
    WHERE rp2.role_id = r.id AND pp2.code = 'menu:dashboard'
  );

COMMIT;

-- 验证：
-- SELECT r.code, p.code FROM roles r JOIN sys_role_permissions rp ON rp.role_id=r.id
--   JOIN sys_permissions p ON p.id=rp.permission_id WHERE p.code='menu:dashboard' ORDER BY r.code;
