-- 工作流复制审计日志表。复制接口会记录源版本、复制选项和节点/连线数量。
CREATE TABLE IF NOT EXISTS `workflow_audit_logs` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `workflow_version_id` BIGINT UNSIGNED NOT NULL COMMENT '目标工作流版本ID',
  `action` VARCHAR(50) NOT NULL COMMENT '操作类型',
  `operator_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '操作人ID',
  `operator_name` VARCHAR(100) DEFAULT NULL COMMENT '操作人名称',
  `details` JSON DEFAULT NULL COMMENT '操作详情',
  `ip_address` VARCHAR(64) DEFAULT NULL,
  `user_agent` VARCHAR(512) DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_workflow_audit_version` (`workflow_version_id`),
  KEY `idx_workflow_audit_action` (`action`),
  KEY `idx_workflow_audit_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工作流审计日志表';
