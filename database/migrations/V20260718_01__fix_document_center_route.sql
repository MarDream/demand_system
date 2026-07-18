-- =====================================================
-- 文档中心入口路由修复 Migration
-- 日期: 2026-07-18
-- 背景: sys_menus 中「文档中心」(id=6) 的 path=/settings/rag，
--       但前端 routes.ts 文档中心路由为 /settings/documents，
--       导致点击侧边栏「文档中心」命中 NotFound 兜底 → 重定向到 /dashboard。
--       同时知识库管理(id=8)与文档中心(id=6)共用权限码 menu:rag，授权粒度丢失。
-- 修复内容:
--   1. sys_permissions: id=9 code 由 menu:rag 改为 menu:document；新增 id=129 menu:knowledge
--   2. sys_menus id=1 仪表盘: component 修正为 views/home/index.vue（DB 元数据，前端不读）
--   3. sys_menus id=6 文档中心: path→/settings/documents, route_name→Documents, permission_code→menu:document
--   4. sys_menus id=8 知识库管理: permission_code→menu:knowledge
--   5. sys_role_permissions: 给 SUPER_ADMIN(role_id=1) 分配 menu:knowledge (permission_id=129)
-- 注意: 全部语句幂等可重复执行
-- =====================================================

-- 1. 权限字典：menu:rag → menu:document，新增 menu:knowledge
UPDATE `sys_permissions`
   SET `code` = 'menu:document',
       `name` = '文档中心菜单',
       `description` = '文档中心入口'
 WHERE `id` = 9;

INSERT IGNORE INTO `sys_permissions` (`id`, `code`, `name`, `type`, `description`, `status`)
VALUES (129, 'menu:knowledge', '知识库管理菜单', 'MENU', '知识库管理入口', 1);

-- 2. 仪表盘 component 元数据修正（前端路由用 routes.ts，此字段仅元数据）
UPDATE `sys_menus`
   SET `component` = 'views/home/index.vue'
 WHERE `id` = 1;

-- 3. 文档中心菜单：path / route_name / permission_code
UPDATE `sys_menus`
   SET `path`           = '/settings/documents',
       `route_name`     = 'Documents',
       `permission_code` = 'menu:document'
 WHERE `id` = 6;

-- 4. 知识库管理菜单：permission_code 拆分
UPDATE `sys_menus`
   SET `permission_code` = 'menu:knowledge'
 WHERE `id` = 8;

-- 5. SUPER_ADMIN 授予新的知识库管理权限
INSERT IGNORE INTO `sys_role_permissions` (`role_id`, `permission_id`, `granted_by`)
VALUES (1, 129, 1);

-- =====================================================
-- 校验查询（执行后人工核对）
-- =====================================================
-- SELECT id, code, name FROM sys_permissions WHERE id IN (9, 129);
-- SELECT id, name, path, route_name, component, permission_code FROM sys_menus WHERE id IN (1, 6, 8);
-- SELECT role_id, permission_id FROM sys_role_permissions WHERE role_id = 1 AND permission_id IN (9, 129);
