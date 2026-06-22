-- V20260622_01__bind_type_to_workflow.sql
-- 需求类型 ↔ 工作流版本 一对一绑定（每个需求类型一个独立工作流版本）
--
-- 背景：
--   1. 工作流原按 projectId 维度绑定，现在改为按 requirement_types.code 维度绑定
--   2. 取消全局默认工作流，未绑定的需求类型在新建需求时不出现
--   3. 新旧引擎（WorkflowDefinitionEngine + StateMachine）共用 WorkflowVersionResolver.resolveForType
--
-- 数据策略（与用户确认）：
--   - 当前数据库全为测试数据，可清空
--   - 现有唯一工作流版本 v14 (一体化运营运维工作流) 保留为基线
--   - 复制 3 份分别作为 Order / Bug / FEATURE 类型的独立流程
--   - 重命名 v14 为"开发需求工作流"用于 Requirement 类型

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =====================================================
-- 1. 清空测试数据（按外键依赖顺序，从叶子表到主表）
-- =====================================================
TRUNCATE TABLE requirement_approval_evaluations;
TRUNCATE TABLE requirement_custom_field_values;
TRUNCATE TABLE requirement_comments;
TRUNCATE TABLE requirement_history;
TRUNCATE TABLE requirement_relations;
TRUNCATE TABLE requirement_follows;
TRUNCATE TABLE requirements;
TRUNCATE TABLE workflow_countersign_records;
TRUNCATE TABLE workflow_parallel_branches;
TRUNCATE TABLE workflow_instance_transitions;
TRUNCATE TABLE workflow_instances;
TRUNCATE TABLE workflow_transition_records;
TRUNCATE TABLE workflow_migration_logs;

SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================
-- 2. requirement_types 表加 workflow_version_id 列
--    每个需求类型必须绑定到一个已启用 (is_active=1, activation_status='active') 的工作流版本
-- =====================================================
ALTER TABLE `requirement_types`
  ADD COLUMN `workflow_version_id` INT UNSIGNED DEFAULT NULL COMMENT '绑定的工作流版本ID（必须 is_active=1 且 activation_status=active）' AFTER `is_default`,
  ADD INDEX `idx_workflow_version_id` (`workflow_version_id`);

-- =====================================================
-- 3. 重命名 v14 为"开发需求工作流"
-- =====================================================
UPDATE `workflow_versions`
SET `name` = '开发需求工作流'
WHERE `id` = 14;

-- =====================================================
-- 4. 基于 v14 复制 3 份独立工作流
--    - 复制 definition JSON（替换 v14_ 为新版本前缀）
--    - 复制 workflow_nodes（替换 node_id 前缀）
--    - 复制 workflow_edges（替换 source/target 前缀）
--    - properties JSON 中的 v14_ 引用也需同步替换
-- =====================================================

-- 4.1 v15: 业务工单工作流（绑 Order 类型）
INSERT INTO `workflow_versions`
  (`project_id`, `version`, `name`, `definition`, `runtime_hash`, `is_active`, `activation_status`, `activated_at`, `creator_id`, `created_at`)
SELECT
  `project_id`, '2.0.3', '业务工单工作流',
  REPLACE(`definition`, 'v14_', 'v15_'),
  `runtime_hash`, 1, 'active', NOW(), `creator_id`, NOW()
FROM `workflow_versions` WHERE `id` = 14;
SET @v15 = LAST_INSERT_ID();

INSERT INTO `workflow_nodes`
  (`workflow_version_id`, `node_id`, `node_type`, `node_name`, `position_x`, `position_y`,
   `assignee_type`, `assignee_role_id`, `assignee_role_group_id`, `assignee_org_id`, `assignee_user_ids`,
   `timeout_hours`, `timeout_action`, `properties`, `created_at`, `updated_at`)
SELECT
  @v15, REPLACE(`node_id`, 'v14_', 'v15_'), `node_type`, `node_name`, `position_x`, `position_y`,
  `assignee_type`, `assignee_role_id`, `assignee_role_group_id`, `assignee_org_id`, `assignee_user_ids`,
  `timeout_hours`, `timeout_action`,
  CASE WHEN `properties` IS NULL THEN NULL ELSE REPLACE(`properties`, 'v14_', 'v15_') END,
  NOW(), NOW()
FROM `workflow_nodes` WHERE `workflow_version_id` = 14;

INSERT INTO `workflow_edges`
  (`workflow_version_id`, `edge_id`, `source_node_id`, `target_node_id`, `label`, `condition`, `properties`, `created_at`, `updated_at`)
SELECT
  @v15, `edge_id`,
  REPLACE(`source_node_id`, 'v14_', 'v15_'),
  REPLACE(`target_node_id`, 'v14_', 'v15_'),
  `label`, `condition`, `properties`, NOW(), NOW()
FROM `workflow_edges` WHERE `workflow_version_id` = 14;

-- 4.2 v16: 系统缺陷工作流（绑 Bug 类型）
INSERT INTO `workflow_versions`
  (`project_id`, `version`, `name`, `definition`, `runtime_hash`, `is_active`, `activation_status`, `activated_at`, `creator_id`, `created_at`)
SELECT
  `project_id`, '2.0.4', '系统缺陷工作流',
  REPLACE(`definition`, 'v14_', 'v16_'),
  `runtime_hash`, 1, 'active', NOW(), `creator_id`, NOW()
FROM `workflow_versions` WHERE `id` = 14;
SET @v16 = LAST_INSERT_ID();

INSERT INTO `workflow_nodes`
  (`workflow_version_id`, `node_id`, `node_type`, `node_name`, `position_x`, `position_y`,
   `assignee_type`, `assignee_role_id`, `assignee_role_group_id`, `assignee_org_id`, `assignee_user_ids`,
   `timeout_hours`, `timeout_action`, `properties`, `created_at`, `updated_at`)
SELECT
  @v16, REPLACE(`node_id`, 'v14_', 'v16_'), `node_type`, `node_name`, `position_x`, `position_y`,
  `assignee_type`, `assignee_role_id`, `assignee_role_group_id`, `assignee_org_id`, `assignee_user_ids`,
  `timeout_hours`, `timeout_action`,
  CASE WHEN `properties` IS NULL THEN NULL ELSE REPLACE(`properties`, 'v14_', 'v16_') END,
  NOW(), NOW()
FROM `workflow_nodes` WHERE `workflow_version_id` = 14;

INSERT INTO `workflow_edges`
  (`workflow_version_id`, `edge_id`, `source_node_id`, `target_node_id`, `label`, `condition`, `properties`, `created_at`, `updated_at`)
SELECT
  @v16, `edge_id`,
  REPLACE(`source_node_id`, 'v14_', 'v16_'),
  REPLACE(`target_node_id`, 'v14_', 'v16_'),
  `label`, `condition`, `properties`, NOW(), NOW()
FROM `workflow_edges` WHERE `workflow_version_id` = 14;

-- 4.3 v17: 功能需求工作流（绑 FEATURE 类型）
INSERT INTO `workflow_versions`
  (`project_id`, `version`, `name`, `definition`, `runtime_hash`, `is_active`, `activation_status`, `activated_at`, `creator_id`, `created_at`)
SELECT
  `project_id`, '2.0.5', '功能需求工作流',
  REPLACE(`definition`, 'v14_', 'v17_'),
  `runtime_hash`, 1, 'active', NOW(), `creator_id`, NOW()
FROM `workflow_versions` WHERE `id` = 14;
SET @v17 = LAST_INSERT_ID();

INSERT INTO `workflow_nodes`
  (`workflow_version_id`, `node_id`, `node_type`, `node_name`, `position_x`, `position_y`,
   `assignee_type`, `assignee_role_id`, `assignee_role_group_id`, `assignee_org_id`, `assignee_user_ids`,
   `timeout_hours`, `timeout_action`, `properties`, `created_at`, `updated_at`)
SELECT
  @v17, REPLACE(`node_id`, 'v14_', 'v17_'), `node_type`, `node_name`, `position_x`, `position_y`,
  `assignee_type`, `assignee_role_id`, `assignee_role_group_id`, `assignee_org_id`, `assignee_user_ids`,
  `timeout_hours`, `timeout_action`,
  CASE WHEN `properties` IS NULL THEN NULL ELSE REPLACE(`properties`, 'v14_', 'v17_') END,
  NOW(), NOW()
FROM `workflow_nodes` WHERE `workflow_version_id` = 14;

INSERT INTO `workflow_edges`
  (`workflow_version_id`, `edge_id`, `source_node_id`, `target_node_id`, `label`, `condition`, `properties`, `created_at`, `updated_at`)
SELECT
  @v17, `edge_id`,
  REPLACE(`source_node_id`, 'v14_', 'v17_'),
  REPLACE(`target_node_id`, 'v14_', 'v17_'),
  `label`, `condition`, `properties`, NOW(), NOW()
FROM `workflow_edges` WHERE `workflow_version_id` = 14;

-- =====================================================
-- 5. 绑定 4 个需求类型到 4 个独立工作流版本
-- =====================================================
UPDATE `requirement_types` SET `workflow_version_id` = 14     WHERE `code` = 'Requirement';
UPDATE `requirement_types` SET `workflow_version_id` = @v15   WHERE `code` = 'Order';
UPDATE `requirement_types` SET `workflow_version_id` = @v16   WHERE `code` = 'Bug';
UPDATE `requirement_types` SET `workflow_version_id` = @v17   WHERE `code` = 'FEATURE';

-- =====================================================
-- 6. 验证：所有需求类型都已绑定活跃工作流
-- =====================================================
SELECT
  rt.code           AS type_code,
  rt.name           AS type_name,
  rt.workflow_version_id,
  wv.name           AS workflow_name,
  wv.activation_status,
  wv.is_active
FROM `requirement_types` rt
LEFT JOIN `workflow_versions` wv ON wv.id = rt.workflow_version_id
ORDER BY rt.id;
