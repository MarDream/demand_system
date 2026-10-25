-- 需求附件删除权限点（2026-10-25）
-- 规则：草稿期拥有我的待办视图权限的用户可删自己上传的附件；
--       提交审核后需赋权 button:requirement:attachment:delete 才能删除需求附件。
-- 用法: docker exec -i mysql mysql -uroot -padmin123 --default-character-set=utf8mb4 demand_system < 本文件

INSERT IGNORE INTO `sys_permissions` (`id`, `code`, `name`, `type`, `description`, `status`) VALUES
(142, 'button:requirement:attachment:delete', '删除需求附件', 'BUTTON', '需求管理-删除附件(需求提交审核后需赋权才能删除)', 1);

-- 菜单 id 走自增（历史库 id 已被自增菜单占用），按 permission_code 判重
INSERT IGNORE INTO `sys_menus`
  (`parent_id`, `name`, `menu_type`, `path`, `route_name`, `component`, `icon`, `sort_order`, `permission_code`, `visible`, `enabled`, `keep_alive`)
SELECT
  2, '删除需求附件', 'BUTTON', NULL, NULL, NULL, NULL, 25, 'button:requirement:attachment:delete', 1, 1, 0
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_menus` WHERE `permission_code` = 'button:requirement:attachment:delete'
);

-- SUPER_ADMIN(id=1) 全量权限清单补授
INSERT IGNORE INTO `sys_role_permissions` (`role_id`, `permission_id`, `granted_by`) VALUES
(1, 142, 1);
