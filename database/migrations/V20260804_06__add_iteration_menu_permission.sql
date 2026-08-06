-- 为“迭代管理”一级菜单补充独立菜单权限。
-- 原因：sys_menus.id=3 的 permission_code 为空时，当前用户菜单过滤会把它视为公共菜单，
-- 即使角色没有任何迭代按钮权限也会显示入口。

INSERT INTO `sys_permissions` (`code`, `name`, `type`, `description`, `status`)
SELECT 'menu:iteration', '迭代管理菜单', 'MENU', '迭代管理一级菜单入口', 1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_permissions` WHERE `code` = 'menu:iteration'
);

UPDATE `sys_menus`
SET `permission_code` = 'menu:iteration'
WHERE `id` = 3
  AND (`permission_code` IS NULL OR `permission_code` = '');

SET @iteration_menu_permission_id := (
    SELECT `id` FROM `sys_permissions` WHERE `code` = 'menu:iteration' LIMIT 1
);

-- 兼容已有角色：如果角色已经拥有任一迭代按钮权限，则补授迭代菜单入口权限。
INSERT IGNORE INTO `sys_role_permissions` (`role_id`, `permission_id`, `granted_by`)
SELECT DISTINCT rp.`role_id`, @iteration_menu_permission_id, 1
FROM `sys_role_permissions` rp
JOIN `sys_permissions` p ON p.`id` = rp.`permission_id`
WHERE @iteration_menu_permission_id IS NOT NULL
  AND p.`code` IN (
      'button:iteration:create',
      'button:iteration:update',
      'button:iteration:delete',
      'button:iteration:view'
  );
