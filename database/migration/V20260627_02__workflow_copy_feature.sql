-- =====================================================
-- 工作流复制功能 - 数据库迁移脚本
-- 版本: V20260627_02
-- 说明: 为工作流版本表添加复制相关字段，创建审计日志表
-- =====================================================

SET NAMES utf8mb4;

-- 1. 修改 workflow_versions 表，添加复制相关字段
ALTER TABLE `workflow_versions`
    ADD COLUMN `source_version_id` INT UNSIGNED NULL COMMENT '源工作流版本ID（复制来源）' AFTER `project_id`,
    ADD COLUMN `is_template` TINYINT DEFAULT 0 COMMENT '是否标记为模板 0=否 1=是' AFTER `is_active`,
    ADD COLUMN `copy_count` INT DEFAULT 0 COMMENT '被复制次数' AFTER `is_template`,
    ADD INDEX `idx_source_version` (`source_version_id`),
    ADD INDEX `idx_template` (`is_template`, `activation_status`);

-- 添加外键约束（软引用，删除源工作流时置空）
ALTER TABLE `workflow_versions`
    ADD CONSTRAINT `fk_source_version`
    FOREIGN KEY (`source_version_id`)
    REFERENCES `workflow_versions`(`id`)
    ON DELETE SET NULL;

-- 2. 创建工作流审计日志表
DROP TABLE IF EXISTS `workflow_audit_logs`;
CREATE TABLE `workflow_audit_logs` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `workflow_version_id` INT UNSIGNED NOT NULL COMMENT '工作流版本ID',
  `action` VARCHAR(50) NOT NULL COMMENT '操作类型: created/copied/modified/deleted/activated',
  `operator_id` INT UNSIGNED NOT NULL COMMENT '操作人ID',
  `operator_name` VARCHAR(100) NOT NULL COMMENT '操作人姓名',
  `details` JSON DEFAULT NULL COMMENT '详细信息（记录变更内容）',
  `ip_address` VARCHAR(45) DEFAULT NULL COMMENT 'IP地址',
  `user_agent` TEXT DEFAULT NULL COMMENT 'User Agent',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  INDEX `idx_workflow_version` (`workflow_version_id`, `created_at` DESC),
  INDEX `idx_operator` (`operator_id`, `created_at` DESC),
  INDEX `idx_action` (`action`, `created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流审计日志表';

-- 3. 为现有工作流版本初始化默认值
UPDATE `workflow_versions` 
SET `source_version_id` = NULL,
    `is_template` = 0,
    `copy_count` = 0
WHERE `source_version_id` IS NULL;

-- 4. 插入初始审计日志（为已存在的工作流版本）
INSERT INTO `workflow_audit_logs` 
  (`workflow_version_id`, `action`, `operator_id`, `operator_name`, `details`, `created_at`)
SELECT 
  `id`,
  'created',
  `creator_id`,
  (SELECT `real_name` FROM `users` WHERE `id` = `workflow_versions`.`creator_id`),
  JSON_OBJECT(
    'version', `version`,
    'name', `name`,
    'migration_note', '数据迁移自动生成的审计记录'
  ),
  `created_at`
FROM `workflow_versions`
WHERE `created_at` IS NOT NULL;

-- 5. 添加数据完整性检查
-- 确保 copy_count 不为负数
ALTER TABLE `workflow_versions`
    ADD CONSTRAINT `chk_copy_count_positive` CHECK (`copy_count` >= 0);

COMMIT;
