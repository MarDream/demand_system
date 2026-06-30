package com.demand.system.module.workflow.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.workflow.dto.ApproveWorkflowRequest;
import com.demand.system.module.workflow.dto.MigrationResultDTO;
import com.demand.system.module.workflow.dto.PublishWorkflowRequest;
import com.demand.system.module.workflow.dto.WorkflowVersionVO;
import com.demand.system.module.workflow.service.WorkflowMigrationService;
import com.demand.system.module.workflow.service.WorkflowVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流版本管理控制器
 */
@RestController
@RequestMapping("/api/v1/workflow/versions")
@Tag(name = "工作流版本管理", description = "工作流版本发布、审批、迁移")
public class WorkflowVersionController {

    private final WorkflowVersionService workflowVersionService;
    private final WorkflowMigrationService workflowMigrationService;

    public WorkflowVersionController(WorkflowVersionService workflowVersionService,
                                    WorkflowMigrationService workflowMigrationService) {
        this.workflowVersionService = workflowVersionService;
        this.workflowMigrationService = workflowMigrationService;
    }

    @PostMapping("/publish")
    @Operation(summary = "发布工作流新版本", description = "提交工作流定义并进入审批流程")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:workflow:config')")
    public Result<Map<String, Object>> publishWorkflow(@RequestBody @Valid PublishWorkflowRequest request) {
        Long versionId = workflowVersionService.publishWorkflow(
                request.getProjectId(),
                request.getRequirementTypeId(),
                request.getDefinition(),
                request.getChangeLog()
        );

        Map<String, Object> result = new HashMap<>();
        result.put("versionId", versionId);
        result.put("message", "工作流已提交审批，审批通过后可启用并绑定需求类型");
        return Result.success(result);
    }

    @PostMapping("/approve")
    @Operation(summary = "审批工作流版本", description = "审批通过后启用该版本，具体使用关系由需求类型绑定决定")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN')")
    public Result<Void> approveWorkflow(@RequestBody @Valid ApproveWorkflowRequest request) {
        workflowVersionService.approveWorkflow(
                request.getVersionId(),
                request.getApproved(),
                request.getComment()
        );

        String message = request.getApproved() ? "审批通过，工作流已启用" : "审批已驳回";
        return Result.success();
    }

    @GetMapping("/list")
    @Operation(summary = "查询工作流版本列表", description = "系统维度，按时间倒序展示所有版本")
    public Result<List<WorkflowVersionVO>> listVersions(
            @RequestParam(required = false) Long projectId
    ) {
        List<WorkflowVersionVO> versions = workflowVersionService.listVersions(projectId);
        return Result.success(versions);
    }

    @GetMapping("/{versionId}/running-count")
    @Operation(summary = "查询版本关联的运行中工单数", description = "用于判断是否可以安全迁移")
    public Result<Map<String, Long>> getRunningInstanceCount(@PathVariable Long versionId) {
        Long count = workflowMigrationService.countRunningInstances(versionId);
        Map<String, Long> result = new HashMap<>();
        result.put("runningCount", count);
        return Result.success(result);
    }

    @PostMapping("/migrate")
    @Operation(summary = "执行工作流版本迁移", description = "根据迁移计划ID执行存量工单迁移")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN')")
    public Result<MigrationResultDTO> migrateVersion(
            @RequestParam Long planId
    ) {
        MigrationResultDTO result =
                workflowMigrationService.executeMigration(planId);
        return Result.success(result);
    }
}
