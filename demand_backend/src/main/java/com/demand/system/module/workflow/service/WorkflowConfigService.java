package com.demand.system.module.workflow.service;

import com.demand.system.module.workflow.dto.WorkflowApprovalDTO;
import com.demand.system.module.workflow.dto.WorkflowConfigDTO;
import com.demand.system.module.workflow.dto.WorkflowVersionActivationDTO;
import com.demand.system.module.workflow.dto.WorkflowVersionMetaUpdateDTO;
import com.demand.system.module.workflow.dto.WorkflowValidationReport;
import com.demand.system.module.workflow.dto.WorkflowVersionDTO;

import java.util.List;

public interface WorkflowConfigService {

    WorkflowConfigDTO getWorkflowConfig(Long projectId);

    /**
     * 保存工作流配置（草稿）
     */
    WorkflowVersionDTO saveWorkflowConfig(Long projectId, WorkflowConfigDTO configDTO);

    void submitForApproval(Long projectId, Long versionId);

    List<WorkflowVersionDTO> getVersionHistory(Long projectId);

    /**
     * 获取全部已启用工作流版本
     */
    List<WorkflowVersionDTO> listActiveVersions();

    WorkflowVersionDTO getVersionConfig(Long versionId);

    WorkflowVersionDTO updateVersionMeta(Long versionId, WorkflowVersionMetaUpdateDTO updateDTO);

    WorkflowVersionDTO updateVersionActivation(Long versionId, WorkflowVersionActivationDTO activationDTO);

    void deleteVersion(Long versionId);

    /**
     * 获取待审核列表（仅超级管理员）
     */
    List<WorkflowApprovalDTO> getPendingApprovals();

    /**
     * 获取审核记录列表（仅超级管理员）
     */
    List<WorkflowApprovalDTO> getWorkflowApprovals();

    void approveWorkflow(Long approvalId, String comment);

    void rejectWorkflow(Long approvalId, String comment);

    void deleteApproval(Long approvalId);

    void clearAllApprovals();

    WorkflowValidationReport validateLatestDraft(Long projectId, Long versionId);

    WorkflowValidationReport validateVersionReport(Long versionId);

    List<com.demand.system.module.workflow.dto.WorkflowValidationIssue> validateVersion(Long versionId);

    /**
     * 预校验工作流配置（不持久化）
     */
    WorkflowValidationReport validateConfig(WorkflowConfigDTO configDTO);
}