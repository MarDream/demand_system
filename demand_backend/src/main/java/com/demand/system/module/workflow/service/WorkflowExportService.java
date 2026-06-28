package com.demand.system.module.workflow.service;

import com.demand.system.module.workflow.dto.WorkflowExportDTO;

public interface WorkflowExportService {

    /**
     * 导出审核通过的工作流版本。
     */
    WorkflowExportDTO exportWorkflow(Long versionId);
}
