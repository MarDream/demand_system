-- 仓库管理菜单（系统配置 → 仓库管理）
-- 页面组件: views/settings/git/repositories.vue；路由: /settings/git/repositories（路由已在 routes.ts 注册）
-- 权限码复用 menu:system-config（与前端路由 meta 一致，超管/admin 均可访问）
-- 说明: sys_menus.id 使用自增（勿写死 id，避免与既有数据冲突）；parent_id=7 为「系统配置」目录
INSERT IGNORE INTO `sys_menus`
  (`parent_id`, `name`, `menu_type`, `path`, `route_name`, `component`, `icon`, `sort_order`, `permission_code`, `visible`, `enabled`, `keep_alive`)
SELECT
  7, '仓库管理', 'MENU', '/settings/git/repositories', 'GitRepositories', 'views/settings/git/repositories.vue', 'FolderOpened', 10, 'menu:system-config', 1, 1, 0
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_menus` WHERE `path` = '/settings/git/repositories' OR `route_name` = 'GitRepositories'
);
