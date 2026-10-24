package com.demand.system.module.workflow.service;

import com.demand.system.module.workflow.dto.WorkflowMigrationReportDTO;

/**
 * 工作流运行时迁移服务：提供一次性存量数据迁移工具，
 * 不提供定时或引擎操作前的自动对齐能力。
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
