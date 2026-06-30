package com.demand.system.module.workflow.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.workflow.dto.WorkflowMigrationReportDTO;
import com.demand.system.module.workflow.service.WorkflowRuntimeMigrationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作流版本迁移控制器
 *
 * ADR-002 变更（2026-06-30）：
 * - 移除 /migrate-running-instances 端点（不再自动对齐）
 * - 保留 /mark-legacy 和 /backfill-instances 端点
 * - 新增迁移计划管理端点（见 WorkflowMigrationPlanController）
 */
@RestController
@RequestMapping("/api/v1/admin/workflow-migration")
public class WorkflowMigrationController {

    private final WorkflowRuntimeMigrationService workflowRuntimeMigrationService;

    public WorkflowMigrationController(WorkflowRuntimeMigrationService workflowRuntimeMigrationService) {
        this.workflowRuntimeMigrationService = workflowRuntimeMigrationService;
    }

    @PostMapping("/mark-legacy")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN')")
    public Result<WorkflowMigrationReportDTO> markLegacy() {
        return Result.success(workflowRuntimeMigrationService.markLegacyRequirements());
    }

    @PostMapping("/backfill-instances")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN')")
    public Result<WorkflowMigrationReportDTO> backfillInstances() {
        return Result.success(workflowRuntimeMigrationService.backfillInstances());
    }
}
