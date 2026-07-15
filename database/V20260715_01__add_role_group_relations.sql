-- 角色多分组支持 - 数据库变更
-- 说明:
--   1. role_groups 新增 is_default 字段，标记默认分组
--   2. roles 新增 is_default 字段，标记默认角色
--   3. 新增 role_group_relations 关联表，支持角色多对多分组
--   4. 数据迁移：现有角色关联写入关联表

-- ============================================================
-- 1. role_groups 表新增 is_default 字段
-- ============================================================
ALTER TABLE `role_groups`
  ADD COLUMN `is_default` TINYINT DEFAULT 0 COMMENT '是否默认分组 0=否 1=是';

-- ============================================================
-- 2. roles 表新增 is_default 字段
-- ============================================================
ALTER TABLE `roles`
  ADD COLUMN `is_default` TINYINT DEFAULT 0 COMMENT '是否默认角色 0=否 1=是';

-- ============================================================
-- 3. 新增 role_group_relations 关联表（多对多）
-- ============================================================
CREATE TABLE IF NOT EXISTS `role_group_relations` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `role_id` INT UNSIGNED NOT NULL COMMENT '角色ID',
  `role_group_id` INT UNSIGNED NOT NULL COMMENT '角色组ID',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_role_group` (`role_id`, `role_group_id`),
  INDEX `idx_role_id` (`role_id`),
  INDEX `idx_role_group_id` (`role_group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色组关联表(多对多)';

-- ============================================================
-- 4. 数据迁移：将现有 role_group_id 不为空的角色写入关联表
-- ============================================================
INSERT INTO role_group_relations (role_id, role_group_id, created_at)
SELECT id, role_group_id, NOW() FROM roles
WHERE role_group_id IS NOT NULL AND deleted_at = 0;