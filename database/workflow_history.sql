-- =====================================================
-- 工作流配置 - 修改历史记录表
-- 说明: 记录工作流版本每次修改的内容，用于时间线展示
-- 参考: requirement_history 的 fieldName/oldValue/newValue 模式
-- =====================================================

DROP TABLE IF EXISTS `workflow_history`;
CREATE TABLE `workflow_history` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `workflow_version_id` BIGINT UNSIGNED NOT NULL COMMENT '工作流版本ID(workflow_versions.id)',
  `project_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '所属项目ID(冗余，方便按项目查询)',
  `operator_id` INT UNSIGNED NOT NULL COMMENT '操作人ID(users.id)',
  `action` ENUM('create','update','activate','deactivate','delete','copy','export','import','publish') NOT NULL DEFAULT 'update' COMMENT '操作类型',
  `change_summary` VARCHAR(500) DEFAULT NULL COMMENT '修改摘要(人类可读的描述，如"新增节点:审批→完成")',
  `change_log` TEXT DEFAULT NULL COMMENT '详细变更内容(JSON格式，记录field/oldValue/newValue列表)',
  `version_snapshot` TEXT DEFAULT NULL COMMENT '操作后的状态快照(JSON，含节点数/连线数/状态数)',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  INDEX `idx_version_id` (`workflow_version_id`),
  INDEX `idx_project_id` (`project_id`),
  INDEX `idx_operator_id` (`operator_id`),
  INDEX `idx_action` (`action`),
  INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工作流修改历史记录表';
