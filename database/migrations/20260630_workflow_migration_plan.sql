-- ============================================================
-- ADR-002: 工作流版本快照优化 — 迁移计划支持
-- 日期: 2026-06-30
-- 说明:
--   1. 扩展 workflow_migration_logs 表，增加节点映射字段
--   2. 新增 workflow_migration_plans 表，支持显式节点映射的手动迁移
-- ============================================================

-- -----------------------------------------------------------
-- 1. 扩展 workflow_migration_logs 表
-- -----------------------------------------------------------
ALTER TABLE workflow_migration_logs
  ADD COLUMN from_node_id VARCHAR(64) DEFAULT NULL COMMENT '源节点ID（迁移时实例所在节点）' AFTER to_version_id,
  ADD COLUMN to_node_id VARCHAR(64) DEFAULT NULL COMMENT '目标节点ID（映射后的新节点）' AFTER from_node_id,
  ADD COLUMN from_node_name VARCHAR(100) DEFAULT NULL COMMENT '源节点名称' AFTER to_node_id,
  ADD COLUMN to_node_name VARCHAR(100) DEFAULT NULL COMMENT '目标节点名称' AFTER from_node_name,
  ADD COLUMN node_mapping_json JSON DEFAULT NULL COMMENT '完整节点映射表(批量迁移时记录全量映射)' AFTER to_node_name,
  ADD COLUMN instance_id BIGINT DEFAULT NULL COMMENT '工作流实例ID' AFTER requirement_id,
  ADD COLUMN plan_id BIGINT DEFAULT NULL COMMENT '关联迁移计划ID' AFTER instance_id;

-- 为新字段创建索引
CREATE INDEX idx_migration_log_instance ON workflow_migration_logs(instance_id);
CREATE INDEX idx_migration_log_plan ON workflow_migration_logs(plan_id);

-- -----------------------------------------------------------
-- 2. 新增 workflow_migration_plans 表
-- -----------------------------------------------------------
CREATE TABLE workflow_migration_plans (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  from_version_id BIGINT NOT NULL COMMENT '源版本ID',
  to_version_id BIGINT NOT NULL COMMENT '目标版本ID',
  project_id BIGINT NOT NULL COMMENT '项目ID',
  node_mapping JSON NOT NULL COMMENT '节点映射配置: [{"fromNodeId":"node_1","toNodeId":"node_5","fromNodeName":"审批","toNodeName":"审批"}]',
  status VARCHAR(20) NOT NULL DEFAULT 'draft' COMMENT '计划状态: draft/pending/executing/completed/failed',
  total_instance_count INT DEFAULT 0 COMMENT '待迁移实例总数',
  migrated_count INT DEFAULT 0 COMMENT '已成功迁移数',
  failed_count INT DEFAULT 0 COMMENT '迁移失败数',
  operator_id BIGINT DEFAULT NULL COMMENT '操作人ID',
  started_at DATETIME DEFAULT NULL COMMENT '执行开始时间',
  completed_at DATETIME DEFAULT NULL COMMENT '执行完成时间',
  remark TEXT DEFAULT NULL COMMENT '备注',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_from_version (from_version_id),
  INDEX idx_to_version (to_version_id),
  INDEX idx_status (status),
  INDEX idx_project (project_id)
) COMMENT '工作流版本迁移计划（ADR-002）';
