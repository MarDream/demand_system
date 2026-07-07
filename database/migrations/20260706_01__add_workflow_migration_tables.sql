-- ============================================================
-- 工作流版本迁移相关表（ADR-002 版本快照 + 迁移）
-- 创建时间: 2026-07-06
-- ============================================================

-- 迁移计划表
CREATE TABLE IF NOT EXISTS `workflow_migration_plans` (
    `id`                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    `from_version_id`       BIGINT          NOT NULL COMMENT '源版本ID',
    `to_version_id`         BIGINT          NOT NULL COMMENT '目标版本ID',
    `project_id`            BIGINT          DEFAULT NULL COMMENT '项目ID',
    `node_mapping`          JSON            DEFAULT NULL COMMENT '节点映射配置 [{fromNodeId,toNodeId,fromNodeName,toNodeName}]',
    `status`                VARCHAR(32)     NOT NULL DEFAULT 'draft' COMMENT '状态: draft/pending/executing/completed/failed',
    `total_instance_count`  INT             DEFAULT 0 COMMENT '受影响实例总数',
    `migrated_count`        INT             DEFAULT 0 COMMENT '已迁移实例数',
    `failed_count`          INT             DEFAULT 0 COMMENT '迁移失败实例数',
    `operator_id`           BIGINT          DEFAULT NULL COMMENT '操作人ID',
    `started_at`            DATETIME        DEFAULT NULL COMMENT '开始执行时间',
    `completed_at`          DATETIME        DEFAULT NULL COMMENT '执行完成时间',
    `remark`                VARCHAR(500)    DEFAULT NULL COMMENT '备注',
    `created_at`            DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`            DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_from_version` (`from_version_id`),
    INDEX `idx_to_version` (`to_version_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流版本迁移计划';

-- 迁移日志表
CREATE TABLE IF NOT EXISTS `workflow_migration_logs` (
    `id`                BIGINT AUTO_INCREMENT PRIMARY KEY,
    `from_version_id`   BIGINT          DEFAULT NULL COMMENT '源版本ID',
    `to_version_id`     BIGINT          DEFAULT NULL COMMENT '目标版本ID',
    `from_node_id`      VARCHAR(64)     DEFAULT NULL COMMENT '源节点ID',
    `to_node_id`        VARCHAR(64)     DEFAULT NULL COMMENT '目标节点ID',
    `from_node_name`    VARCHAR(128)    DEFAULT NULL COMMENT '源节点名称',
    `to_node_name`      VARCHAR(128)    DEFAULT NULL COMMENT '目标节点名称',
    `node_mapping_json` JSON            DEFAULT NULL COMMENT '完整节点映射JSON',
    `requirement_id`    BIGINT          DEFAULT NULL COMMENT '关联需求ID',
    `instance_id`       BIGINT          DEFAULT NULL COMMENT '工作流实例ID',
    `plan_id`           BIGINT          DEFAULT NULL COMMENT '关联迁移计划ID',
    `migration_type`    VARCHAR(32)     DEFAULT NULL COMMENT '迁移类型',
    `migration_status`  VARCHAR(32)     NOT NULL DEFAULT 'pending' COMMENT '迁移状态: pending/success/failed',
    `error_message`     TEXT            DEFAULT NULL COMMENT '错误信息',
    `operator_id`       BIGINT          DEFAULT NULL COMMENT '操作人ID',
    `created_at`        DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_plan` (`plan_id`),
    INDEX `idx_requirement` (`requirement_id`),
    INDEX `idx_instance` (`instance_id`),
    INDEX `idx_status` (`migration_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流版本迁移日志';
