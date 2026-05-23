package com.demand.system.module.workflow.service;

import com.demand.system.module.workflow.dto.WorkflowMigrationReportDTO;

public interface WorkflowRuntimeMigrationService {

    WorkflowMigrationReportDTO markLegacyRequirements();

    WorkflowMigrationReportDTO backfillInstances();
}
