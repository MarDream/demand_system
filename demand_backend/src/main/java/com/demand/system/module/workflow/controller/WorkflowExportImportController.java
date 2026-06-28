package com.demand.system.module.workflow.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.workflow.dto.WorkflowExportDTO;
import com.demand.system.module.workflow.dto.WorkflowImportResponseDTO;
import com.demand.system.module.workflow.service.WorkflowExportService;
import com.demand.system.module.workflow.service.WorkflowImportService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowExportImportController {

    private final WorkflowExportService workflowExportService;
    private final WorkflowImportService workflowImportService;

    public WorkflowExportImportController(WorkflowExportService workflowExportService,
                                          WorkflowImportService workflowImportService) {
        this.workflowExportService = workflowExportService;
        this.workflowImportService = workflowImportService;
    }

    /**
     * 导出审核通过的工作流版本。
     */
    @GetMapping("/versions/{versionId}/export")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:workflow:config', 'button:workflow:export')")
    public Result<WorkflowExportDTO> exportWorkflow(@PathVariable Long versionId) {
        return Result.success(workflowExportService.exportWorkflow(versionId));
    }

    /**
     * 导入工作流为新的草稿版本。
     */
    @PostMapping("/import")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:workflow:config', 'button:workflow:import')")
    public Result<WorkflowImportResponseDTO> importWorkflow(@RequestBody WorkflowExportDTO data,
                                                            @RequestParam(defaultValue = "0") Long projectId) {
        return Result.success(workflowImportService.importWorkflow(data, projectId));
    }
}
