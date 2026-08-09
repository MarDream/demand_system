-- AI 助手设置菜单（系统配置 → AI 助手设置）
-- 页面组件: views/settings/assistant.vue；路由: /settings/assistant（路由已在 routes.ts 注册）
-- 权限码复用 menu:system-config（与前端路由 meta 一致，超管/admin 均可访问）
-- 说明: 数据库 sys_menus.id 使用自增（勿写死 id，避免与既有数据冲突）
INSERT IGNORE INTO `sys_menus`
  (`parent_id`, `name`, `menu_type`, `path`, `route_name`, `component`, `icon`, `sort_order`, `permission_code`, `visible`, `enabled`, `keep_alive`)
SELECT
  7, 'AI 助手设置', 'MENU', '/settings/assistant', 'AssistantSettings', 'views/settings/assistant.vue', 'ChatDotRound', 8, 'menu:system-config', 1, 1, 0
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_menus` WHERE `path` = '/settings/assistant' OR `route_name` = 'AssistantSettings'
);
