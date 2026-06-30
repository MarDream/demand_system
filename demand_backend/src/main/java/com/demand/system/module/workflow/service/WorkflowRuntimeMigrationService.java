package com.demand.system.module.workflow.service;

import com.demand.system.module.workflow.dto.WorkflowMigrationReportDTO;

/**
 * 工作流运行时迁移服务
 *
 * ADR-002 变更（2026-06-30）：
 * - 移除 alignRunningInstancesToActiveVersion() — 不再定时自动对齐
 * - 移除 alignRequirementInstanceIfNeeded() — 引擎操作前不再自动对齐
 * - 保留 markLegacyRequirements() 和 backfillInstances() — 一次性迁移工具
 */
public interface WorkflowRuntimeMigrationService {

    /**
     * 标记无工作流实例且项目无活跃工作流定义的旧需求为 legacy
     */
    WorkflowMigrationReportDTO markLegacyRequirements();

    /**
     * 为有活跃工作流定义但无实例的非草稿需求补建工作流实例
     */
    WorkflowMigrationReportDTO backfillInstances();
}
