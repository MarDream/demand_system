-- ====================================
-- 工作流版本管理与节点审计增强 - 增量迁移
-- 执行时间: 2026-06-20
-- ====================================

-- 1. 新增工单类型表（如果不存在）
CREATE TABLE IF NOT EXISTS `requirement_types` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(50) NOT NULL COMMENT '类型编码（如 DEMAND/BUG/TASK）',
  `name` VARCHAR(100) NOT NULL COMMENT '类型名称',
  `project_id` INT UNSIGNED DEFAULT NULL COMMENT '所属项目ID（NULL表示系统级）',
  `workflow_version_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '绑定的工作流版本ID',
  `sort_order` INT DEFAULT 0 COMMENT '排序',
  `is_enabled` TINYINT DEFAULT 1 COMMENT '是否启用',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_code` (`project_id`, `code`),
  INDEX `idx_workflow_version` (`workflow_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单类型配置表';

-- 2. 需求表增加工单类型字段（检查后添加）
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                   WHERE TABLE_SCHEMA='demand_system' AND TABLE_NAME='requirements'
                   AND COLUMN_NAME='requirement_type_id');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `requirements` ADD COLUMN `requirement_type_id` INT UNSIGNED DEFAULT NULL COMMENT ''工单类型ID'' AFTER `project_id`, ADD INDEX `idx_requirement_type` (`requirement_type_id`)',
    'SELECT ''Column requirement_type_id already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. 增强 workflow_versions 表 - deprecated_at
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                   WHERE TABLE_SCHEMA='demand_system' AND TABLE_NAME='workflow_versions'
                   AND COLUMN_NAME='deprecated_at');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `workflow_versions` ADD COLUMN `deprecated_at` DATETIME DEFAULT NULL COMMENT ''废弃时间''',
    'SELECT ''Column deprecated_at already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4. 增强 workflow_versions 表 - change_log
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                   WHERE TABLE_SCHEMA='demand_system' AND TABLE_NAME='workflow_versions'
                   AND COLUMN_NAME='change_log');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `workflow_versions` ADD COLUMN `change_log` TEXT COMMENT ''版本变更说明''',
    'SELECT ''Column change_log already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5. 增强 workflow_versions 表 - submitted_for_approval_at
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                   WHERE TABLE_SCHEMA='demand_system' AND TABLE_NAME='workflow_versions'
                   AND COLUMN_NAME='submitted_for_approval_at');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `workflow_versions` ADD COLUMN `submitted_for_approval_at` DATETIME DEFAULT NULL COMMENT ''提交审批时间''',
    'SELECT ''Column submitted_for_approval_at already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 6. 增强 workflow_versions 表 - approved_at
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                   WHERE TABLE_SCHEMA='demand_system' AND TABLE_NAME='workflow_versions'
                   AND COLUMN_NAME='approved_at');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `workflow_versions` ADD COLUMN `approved_at` DATETIME DEFAULT NULL COMMENT ''审批通过时间''',
    'SELECT ''Column approved_at already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 7. 增强 workflow_versions 表 - approved_by
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                   WHERE TABLE_SCHEMA='demand_system' AND TABLE_NAME='workflow_versions'
                   AND COLUMN_NAME='approved_by');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `workflow_versions` ADD COLUMN `approved_by` BIGINT UNSIGNED DEFAULT NULL COMMENT ''审批人ID''',
    'SELECT ''Column approved_by already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 8. 增强 workflow_versions 表 - approval_comment
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                   WHERE TABLE_SCHEMA='demand_system' AND TABLE_NAME='workflow_versions'
                   AND COLUMN_NAME='approval_comment');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `workflow_versions` ADD COLUMN `approval_comment` TEXT COMMENT ''审批意见''',
    'SELECT ''Column approval_comment already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 9. 添加索引（MySQL 8.0+ 支持 IF NOT EXISTS）
CREATE INDEX IF NOT EXISTS `idx_activation_status` ON `workflow_versions` (`activation_status`);
CREATE INDEX IF NOT EXISTS `idx_project_active` ON `workflow_versions` (`project_id`, `is_active`, `activation_status`);

-- 10. 新增工作流版本迁移日志表
CREATE TABLE IF NOT EXISTS `workflow_migration_logs` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `from_version_id` BIGINT UNSIGNED NOT NULL COMMENT '源版本ID',
  `to_version_id` BIGINT UNSIGNED NOT NULL COMMENT '目标版本ID',
  `requirement_id` BIGINT UNSIGNED NOT NULL COMMENT '需求ID',
  `migration_type` VARCHAR(20) NOT NULL COMMENT 'manual/batch/auto',
  `migration_status` VARCHAR(20) NOT NULL COMMENT 'success/failed/rollback',
  `error_message` TEXT COMMENT '失败原因',
  `operator_id` BIGINT UNSIGNED NOT NULL COMMENT '操作人ID',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_from_version` (`from_version_id`),
  INDEX `idx_to_version` (`to_version_id`),
  INDEX `idx_requirement` (`requirement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流版本迁移日志';

-- 11. 初始化默认工单类型（系统级）
INSERT INTO `requirement_types` (`code`, `name`, `project_id`, `sort_order`, `is_enabled`)
VALUES
('DEMAND', '需求', NULL, 1, 1),
('BUG', '缺陷', NULL, 2, 1),
('TASK', '任务', NULL, 3, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- 12. 为现有项目绑定默认工单类型
INSERT INTO `requirement_types` (`code`, `name`, `project_id`, `workflow_version_id`, `sort_order`, `is_enabled`)
SELECT
    'DEMAND',
    CONCAT(p.name, '-需求'),
    p.id AS project_id,
    wv.id AS workflow_version_id,
    1,
    1
FROM `projects` p
LEFT JOIN (
    SELECT wv1.project_id, wv1.id
    FROM `workflow_versions` wv1
    WHERE wv1.is_active = 1
      AND wv1.activation_status = 'active'
) wv ON wv.project_id = p.id
WHERE p.id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `requirement_types` rt
    WHERE rt.project_id = p.id AND rt.code = 'DEMAND'
  );

-- 13. 为现有需求设置默认工单类型
UPDATE `requirements` r
LEFT JOIN `requirement_types` rt ON (
    rt.project_id = r.project_id
    AND rt.code = 'DEMAND'
)
LEFT JOIN `requirement_types` rt_default ON (
    rt_default.project_id IS NULL
    AND rt_default.code = 'DEMAND'
)
SET r.requirement_type_id = COALESCE(rt.id, rt_default.id)
WHERE r.requirement_type_id IS NULL;

-- 14. 为现有工作流版本补全 config_hash
UPDATE `workflow_versions`
SET `config_hash` = `runtime_hash`
WHERE `config_hash` IS NULL AND `runtime_hash` IS NOT NULL;
