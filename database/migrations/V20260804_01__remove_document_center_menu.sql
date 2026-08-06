-- 文档中心菜单入口移除 Migration
-- 背景: 文档中心功能已被 AI 操作助手替代，移除菜单入口
-- 影响: sys_menus 中文档中心记录(id=6) 禁用，sys_permissions 中 menu:document(id=9) 禁用
-- 安全: 仅设 enabled=0/status=0，不删数据，可回滚

-- 1. 禁用文档中心菜单
UPDATE sys_menus SET enabled = 0, visible = 0
WHERE id = 6 AND path = '/settings/documents' AND permission_code = 'menu:document';

-- 2. 禁用文档中心权限码
UPDATE sys_permissions SET status = 0
WHERE id = 9 AND code = 'menu:document';

-- 3. 移除角色与文档中心权限的关联
DELETE FROM sys_role_permissions WHERE permission_id = 9;
