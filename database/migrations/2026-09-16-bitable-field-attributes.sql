-- ============================================================
-- 多维表格字段属性补充开发（2026-09-16）
--   1) 日期字段支持「包含时间」：value_date 由 DATE 扩为 DATETIME
--   2) 新增字段级权限表 bitable_field_permissions
-- 执行：docker exec -i mysql mysql --default-character-set=utf8mb4 -uroot -padmin123 demand_system < <本文件>
-- ============================================================

-- 1. 日期字段存储扩展为日期时间
--    原有 DATE 数据（00:00:00）语义不变；开启「包含时间」的字段可存完整时间。
ALTER TABLE `bitable_cell_values`
  MODIFY COLUMN `value_date` DATETIME DEFAULT NULL COMMENT '日期时间值（日期字段未开启“包含时间”时仅日期部分有效）';

-- 2. 字段级权限
DROP TABLE IF EXISTS `bitable_field_permissions`;
CREATE TABLE `bitable_field_permissions` (
  `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `base_id`           BIGINT UNSIGNED NOT NULL COMMENT '多维表格ID',
  `role_type`         VARCHAR(20) NOT NULL COMMENT '角色类型: system=系统角色, custom=自定义角色',
  `system_role_code`  VARCHAR(50) DEFAULT NULL COMMENT '系统角色编码(owner/admin/editor/commenter/viewer)',
  `custom_role_id`    BIGINT UNSIGNED DEFAULT NULL COMMENT '自定义角色ID',
  `table_id`          BIGINT UNSIGNED NOT NULL COMMENT '数据表ID',
  `field_id`          BIGINT UNSIGNED NOT NULL COMMENT '字段ID',
  `permission_level`  VARCHAR(20) NOT NULL DEFAULT 'editable' COMMENT '字段权限级别: editable=可编辑, readonly=只读, hidden=隐藏',
  `creator_id`        BIGINT UNSIGNED NOT NULL COMMENT '创建人ID',
  `created_at`        DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`        DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `role_identifier`   VARCHAR(120) AS (CONCAT(`role_type`, ':', COALESCE(`system_role_code`, ''), ':', COALESCE(`custom_role_id`, ''))) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_field_permission` (`base_id`, `role_identifier`, `field_id`),
  INDEX `idx_base_table` (`base_id`, `table_id`),
  INDEX `idx_field` (`field_id`),
  INDEX `idx_custom_role` (`custom_role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='多维表格-角色字段权限';
