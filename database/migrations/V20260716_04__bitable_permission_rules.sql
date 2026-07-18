-- 多维表格-高级权限规则表（预留，P1 再实现行级/列级权限）
CREATE TABLE IF NOT EXISTS `bitable_permission_rules` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `base_id` BIGINT UNSIGNED NOT NULL COMMENT '多维表格ID',
  `table_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '数据表ID(空=Base级)',
  `subject_type` VARCHAR(20) NOT NULL COMMENT '主体类型: role/user/department',
  `subject_id` VARCHAR(50) NOT NULL COMMENT '主体ID',
  `resource_type` VARCHAR(20) NOT NULL COMMENT '资源类型: base/table/record/field',
  `action` VARCHAR(30) NOT NULL COMMENT '操作: read/create/update/delete/manage',
  `effect` VARCHAR(10) NOT NULL DEFAULT 'allow' COMMENT '效果: allow/deny',
  `condition_config` JSON DEFAULT NULL COMMENT '条件配置(行级/列级)',
  `priority` INT DEFAULT 0 COMMENT '优先级',
  `enabled` TINYINT DEFAULT 1 COMMENT '是否启用',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_base_id` (`base_id`),
  INDEX `idx_table_id` (`table_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='多维表格-高级权限规则';

-- 为 bitable_base_members 添加联合索引（加速权限查询）
CREATE INDEX `idx_base_user` ON `bitable_base_members` (`base_id`, `user_id`);
