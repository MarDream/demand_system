-- Add the System Config top-level menu and move admin menus under it.
-- Safe to run repeatedly against an existing local database.

INSERT INTO `sys_permissions` (`id`, `code`, `name`, `type`, `description`, `status`)
VALUES (1, 'menu:system-config', '系统配置菜单', 'MENU', '系统配置一级菜单入口', 1)
ON DUPLICATE KEY UPDATE
  `code` = VALUES(`code`),
  `name` = VALUES(`name`),
  `type` = VALUES(`type`),
  `description` = VALUES(`description`),
  `status` = VALUES(`status`);

INSERT INTO `sys_menus` (
  `id`, `parent_id`, `name`, `menu_type`, `path`, `route_name`, `component`, `icon`,
  `sort_order`, `permission_code`, `visible`, `enabled`, `keep_alive`
)
VALUES (
  7, 0, '系统配置', 'DIRECTORY', NULL, NULL, NULL, 'Setting',
  6, 'menu:system-config', 1, 1, 0
)
ON DUPLICATE KEY UPDATE
  `parent_id` = VALUES(`parent_id`),
  `name` = VALUES(`name`),
  `menu_type` = VALUES(`menu_type`),
  `path` = VALUES(`path`),
  `route_name` = VALUES(`route_name`),
  `component` = VALUES(`component`),
  `icon` = VALUES(`icon`),
  `sort_order` = VALUES(`sort_order`),
  `permission_code` = VALUES(`permission_code`),
  `visible` = VALUES(`visible`),
  `enabled` = VALUES(`enabled`),
  `keep_alive` = VALUES(`keep_alive`);

UPDATE `sys_menus`
SET `parent_id` = 7,
    `sort_order` = CASE `id`
      WHEN 11 THEN 1
      WHEN 12 THEN 2
      WHEN 13 THEN 3
      WHEN 14 THEN 4
      WHEN 15 THEN 5
      ELSE `sort_order`
    END
WHERE `id` IN (11, 12, 13, 14, 15);

UPDATE `sys_menus`
SET `parent_id` = 0,
    `sort_order` = CASE `id`
      WHEN 6 THEN 7
      WHEN 8 THEN 8
      WHEN 10 THEN 9
      WHEN 16 THEN 10
      ELSE `sort_order`
    END
WHERE `id` IN (6, 8, 10, 16);

INSERT IGNORE INTO `sys_role_permissions` (`role_id`, `permission_id`, `granted_by`)
VALUES (1, 1, 1);
