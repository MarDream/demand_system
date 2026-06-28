package com.demand.system.module.workflow.service;

import com.demand.system.module.workflow.dto.WorkflowExportDTO;
import com.demand.system.module.workflow.dto.WorkflowImportResponseDTO;

public interface WorkflowImportService {

    /**
     * 导入工作流为新的草稿版本。
     */
    WorkflowImportResponseDTO importWorkflow(WorkflowExportDTO data, Long projectId);
}
