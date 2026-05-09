package com.demand.system.module.workflow.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.workflow.dto.ApprovalRequestDTO;
import com.demand.system.module.workflow.dto.WorkflowApprovalDTO;
import com.demand.system.module.workflow.dto.WorkflowConfigDTO;
import com.demand.system.module.workflow.dto.WorkflowVersionDTO;
import com.demand.system.module.workflow.service.WorkflowConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WorkflowVisualConfigController {

    private final WorkflowConfigService workflowConfigService;

    /**
     * 获取当前工作流配置（节点+连线）
     */
    @GetMapping("/workflows/{projectId}/config")
    @PreAuthorize("hasAnyAuthority('admin', 'workflow:config')")
    public Result<WorkflowConfigDTO> getWorkflowConfig(@PathVariable Long projectId) {
        return Result.success(workflowConfigService.getWorkflowConfig(projectId));
    }

    /**
     * 保存工作流配置（草稿）
     */
    @PostMapping("/workflows/{projectId}/config")
    @PreAuthorize("hasAnyAuthority('admin', 'workflow:config')")
    public Result<Void> saveWorkflowConfig(@PathVariable Long projectId,
                                           @RequestBody WorkflowConfigDTO configDTO) {
        workflowConfigService.saveWorkflowConfig(projectId, configDTO);
        return Result.success();
    }

    /**
     * 提交审核
     */
    @PostMapping("/workflows/{projectId}/publish")
    @PreAuthorize("hasAnyAuthority('admin', 'workflow:config')")
    public Result<Void> submitForApproval(@PathVariable Long projectId) {
        workflowConfigService.submitForApproval(projectId);
        return Result.success();
    }

    /**
     * 获取历史版本列表
     */
    @GetMapping("/workflows/{projectId}/versions")
    @PreAuthorize("hasAnyAuthority('admin', 'workflow:config')")
    public Result<List<WorkflowVersionDTO>> getVersionHistory(@PathVariable Long projectId) {
        return Result.success(workflowConfigService.getVersionHistory(projectId));
    }

    /**
     * 获取指定版本配置
     */
    @GetMapping("/workflows/versions/{versionId}")
    @PreAuthorize("hasAnyAuthority('admin', 'workflow:config')")
    public Result<WorkflowVersionDTO> getVersionConfig(@PathVariable Long versionId) {
        return Result.success(workflowConfigService.getVersionConfig(versionId));
    }

    /**
     * 获取待审核列表（仅超级管理员）
     */
    @GetMapping("/workflow-approvals/pending")
    @PreAuthorize("hasAuthority('super_admin')")
    public Result<List<WorkflowApprovalDTO>> getPendingApprovals() {
        return Result.success(workflowConfigService.getPendingApprovals());
    }

    /**
     * 审核通过
     */
    @PostMapping("/workflow-approvals/{id}/approve")
    @PreAuthorize("hasAuthority('super_admin')")
    public Result<Void> approveWorkflow(@PathVariable Long id,
                                       @Valid @RequestBody ApprovalRequestDTO requestDTO) {
        workflowConfigService.approveWorkflow(id, requestDTO.getComment());
        return Result.success();
    }

    /**
     * 审核拒绝
     */
    @PostMapping("/workflow-approvals/{id}/reject")
    @PreAuthorize("hasAuthority('super_admin')")
    public Result<Void> rejectWorkflow(@PathVariable Long id,
                                      @Valid @RequestBody ApprovalRequestDTO requestDTO) {
        workflowConfigService.rejectWorkflow(id, requestDTO.getComment());
        return Result.success();
    }
}
