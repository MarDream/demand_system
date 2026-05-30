package com.demand.system.module.workflow.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.workflow.dto.WorkflowMigrationReportDTO;
import com.demand.system.module.workflow.service.WorkflowRuntimeMigrationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @PostMapping("/migrate-running-instances")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN')")
    public Result<WorkflowMigrationReportDTO> migrateRunningInstances() {
        WorkflowMigrationReportDTO report = new WorkflowMigrationReportDTO();
        report.setMigratedRunningInstanceCount(workflowRuntimeMigrationService.alignRunningInstancesToActiveVersion());
        return Result.success(report);
    }
}
