package com.demand.system.module.workflow.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.workflow.dto.ApprovalRequestDTO;
import com.demand.system.module.workflow.dto.WorkflowApprovalDTO;
import com.demand.system.module.workflow.dto.WorkflowConfigDTO;
import com.demand.system.module.workflow.dto.WorkflowValidationReport;
import com.demand.system.module.workflow.dto.WorkflowVersionActivationDTO;
import com.demand.system.module.workflow.dto.WorkflowVersionMetaUpdateDTO;
import com.demand.system.module.workflow.dto.WorkflowVersionDTO;
import com.demand.system.module.workflow.service.WorkflowConfigService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class WorkflowVisualConfigController {

    private final WorkflowConfigService workflowConfigService;

    public WorkflowVisualConfigController(WorkflowConfigService workflowConfigService) {
        this.workflowConfigService = workflowConfigService;
    }

    /**
     * 获取当前工作流配置（节点+连线）
     */
    @GetMapping("/workflows/{projectId}/config")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:workflow:config')")
    public Result<WorkflowConfigDTO> getWorkflowConfig(@PathVariable Long projectId) {
        return Result.success(workflowConfigService.getWorkflowConfig(projectId));
    }

    /**
     * 保存工作流配置（草稿）
     */
    @PostMapping("/workflows/{projectId}/config")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:workflow:config')")
    public Result<WorkflowVersionDTO> saveWorkflowConfig(@PathVariable Long projectId,
                                                         @RequestBody WorkflowConfigDTO configDTO) {
        return Result.success(workflowConfigService.saveWorkflowConfig(projectId, configDTO));
    }

    /**
     * 提交审核
     */
    @PostMapping("/workflows/{projectId}/publish")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:workflow:config')")
    public Result<Void> submitForApproval(@PathVariable Long projectId) {
        workflowConfigService.submitForApproval(projectId);
        return Result.success();
    }

    /**
     * 获取历史版本列表
     */
    @GetMapping("/workflows/{projectId}/versions")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:workflow:config')")
    public Result<List<WorkflowVersionDTO>> getVersionHistory(@PathVariable Long projectId) {
        return Result.success(workflowConfigService.getVersionHistory(projectId));
    }

    /**
     * 获取全部已启用工作流版本（用于需求类型绑定）
     */
    @GetMapping("/workflows/versions/active")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:workflow:config', 'button:requirement-config:update')")
    public Result<List<WorkflowVersionDTO>> listActiveVersions() {
        return Result.success(workflowConfigService.listActiveVersions());
    }

    /**
     * 获取指定版本配置
     */
    @GetMapping("/workflows/versions/{versionId}")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:workflow:config')")
    public Result<WorkflowVersionDTO> getVersionConfig(@PathVariable Long versionId) {
        return Result.success(workflowConfigService.getVersionConfig(versionId));
    }

    /**
     * 更新版本元数据
     */
    @PutMapping("/workflows/versions/{versionId}/meta")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:workflow:config')")
    public Result<WorkflowVersionDTO> updateVersionMeta(@PathVariable Long versionId,
                                                        @Valid @RequestBody WorkflowVersionMetaUpdateDTO updateDTO) {
        return Result.success(workflowConfigService.updateVersionMeta(versionId, updateDTO));
    }

    /**
     * 更新版本启停状态
     */
    @PutMapping("/workflows/versions/{versionId}/activation")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:workflow:config')")
    public Result<WorkflowVersionDTO> updateVersionActivation(@PathVariable Long versionId,
                                                              @Valid @RequestBody WorkflowVersionActivationDTO activationDTO) {
        return Result.success(workflowConfigService.updateVersionActivation(versionId, activationDTO));
    }

    /**
     * 删除工作流版本
     */
    @DeleteMapping("/workflows/versions/{versionId}")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:workflow:config')")
    public Result<Void> deleteVersion(@PathVariable Long versionId) {
        workflowConfigService.deleteVersion(versionId);
        return Result.success();
    }

    /**
     * 获取待审核列表（仅超级管理员）
     */
    @GetMapping("/workflow-approvals/pending")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:workflow:config', 'menu:settings:workflow')")
    public Result<List<WorkflowApprovalDTO>> getPendingApprovals() {
        return Result.success(workflowConfigService.getPendingApprovals());
    }

    /**
     * 获取审核记录列表（仅超级管理员）
     */
    @GetMapping("/workflow-approvals")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:workflow:config', 'menu:settings:workflow')")
    public Result<List<WorkflowApprovalDTO>> getWorkflowApprovals() {
        return Result.success(workflowConfigService.getWorkflowApprovals());
    }

    /**
     * 审核通过
     */
    @PostMapping("/workflow-approvals/{id}/approve")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN')")
    public Result<Void> approveWorkflow(@PathVariable Long id,
                                       @Valid @RequestBody ApprovalRequestDTO requestDTO) {
        workflowConfigService.approveWorkflow(id, requestDTO.getComment());
        return Result.success();
    }

    /**
     * 审核拒绝
     */
    @PostMapping("/workflow-approvals/{id}/reject")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN')")
    public Result<Void> rejectWorkflow(@PathVariable Long id,
                                      @Valid @RequestBody ApprovalRequestDTO requestDTO) {
        workflowConfigService.rejectWorkflow(id, requestDTO.getComment());
        return Result.success();
    }

    /**
     * 删除单条审核记录
     */
    @DeleteMapping("/workflow-approvals/{id}")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:workflow:approve')")
    public Result<Void> deleteApproval(@PathVariable Long id) {
        workflowConfigService.deleteApproval(id);
        return Result.success();
    }

    /**
     * 清空全部审核记录
     */
    @DeleteMapping("/workflow-approvals")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN')")
    public Result<Void> clearAllApprovals() {
        workflowConfigService.clearAllApprovals();
        return Result.success();
    }

    /**
     * 提交审核前校验最新草稿版本
     */
    @PostMapping("/workflows/{projectId}/validate-before-submit")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:workflow:config')")
    public Result<WorkflowValidationReport> validateBeforeSubmit(@PathVariable Long projectId) {
        return Result.success(workflowConfigService.validateLatestDraft(projectId));
    }

    @PostMapping("/workflows/versions/{versionId}/validate/report")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:workflow:config')")
    public Result<WorkflowValidationReport> validateVersionReport(@PathVariable Long versionId) {
        return Result.success(workflowConfigService.validateVersionReport(versionId));
    }

    @PostMapping("/workflows/versions/{versionId}/validate")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:workflow:config')")
    public Result<List<com.demand.system.module.workflow.dto.WorkflowValidationIssue>> validateVersion(@PathVariable Long versionId) {
        return Result.success(workflowConfigService.validateVersion(versionId));
    }

    /**
     * 预校验工作流配置（不持久化，用于保存草稿前的提示）
     */
    @PostMapping("/workflows/validate-config")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:workflow:config')")
    public Result<WorkflowValidationReport> validateConfig(@RequestBody WorkflowConfigDTO configDTO) {
        return Result.success(workflowConfigService.validateConfig(configDTO));
    }
}