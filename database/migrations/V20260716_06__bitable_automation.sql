-- 多维表格-自动化规则定义
CREATE TABLE IF NOT EXISTS `bitable_automations` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `base_id` BIGINT UNSIGNED NOT NULL COMMENT '多维表格ID',
  `table_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '数据表ID',
  `name` VARCHAR(200) NOT NULL COMMENT '自动化名称',
  `status` VARCHAR(20) NOT NULL DEFAULT 'enabled' COMMENT '状态: enabled/disabled',
  `trigger_type` VARCHAR(50) NOT NULL COMMENT '触发器类型: record_created/record_updated/record_deleted/form_submitted/scheduled',
  `trigger_config` JSON DEFAULT NULL COMMENT '触发器配置(字段变更条件/定时cron等)',
  `action_type` VARCHAR(50) NOT NULL COMMENT '动作类型: update_record/create_record/send_message/http_request',
  `action_config` JSON DEFAULT NULL COMMENT '动作配置(目标字段/消息模板/URL等)',
  `created_by` BIGINT UNSIGNED NOT NULL COMMENT '创建人ID',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` TINYINT DEFAULT 0 COMMENT '0=未删除, 1=已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_base_id` (`base_id`),
  INDEX `idx_table_id` (`table_id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='多维表格-自动化规则';

-- 多维表格-自动化执行记录
CREATE TABLE IF NOT EXISTS `bitable_automation_runs` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `automation_id` BIGINT UNSIGNED NOT NULL COMMENT '自动化规则ID',
  `event_id` VARCHAR(100) DEFAULT NULL COMMENT '触发事件ID(幂等键)',
  `status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '执行状态: pending/running/succeeded/failed',
  `trigger_detail` JSON DEFAULT NULL COMMENT '触发详情(变更字段/值等)',
  `action_result` JSON DEFAULT NULL COMMENT '动作执行结果',
  `error_code` VARCHAR(50) DEFAULT NULL COMMENT '错误码',
  `error_message` TEXT DEFAULT NULL COMMENT '错误信息',
  `attempt` INT DEFAULT 0 COMMENT '重试次数',
  `started_at` DATETIME DEFAULT NULL COMMENT '开始时间',
  `finished_at` DATETIME DEFAULT NULL COMMENT '完成时间',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_automation_id` (`automation_id`),
  INDEX `idx_event_id` (`event_id`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='多维表格-自动化执行记录';
