package com.demand.system.module.workflow.service;

import com.demand.system.module.workflow.dto.WorkflowApprovalDTO;
import com.demand.system.module.workflow.dto.WorkflowConfigDTO;
import com.demand.system.module.workflow.dto.WorkflowVersionActivationDTO;
import com.demand.system.module.workflow.dto.WorkflowVersionMetaUpdateDTO;
import com.demand.system.module.workflow.dto.WorkflowVersionDTO;

import java.util.List;

public interface WorkflowConfigService {

    /**
     * 获取当前工作流配置（节点+连线）
     */
    WorkflowConfigDTO getWorkflowConfig(Long projectId);

    /**
     * 保存工作流配置（草稿）
     */
    WorkflowVersionDTO saveWorkflowConfig(Long projectId, WorkflowConfigDTO configDTO);

    /**
     * 提交审核
     */
    void submitForApproval(Long projectId);

    /**
     * 获取历史版本列表
     */
    List<WorkflowVersionDTO> getVersionHistory(Long projectId);

    /**
     * 获取全部已启用工作流版本
     */
    List<WorkflowVersionDTO> listActiveVersions();

    /**
     * 获取指定版本配置
     */
    WorkflowVersionDTO getVersionConfig(Long versionId);

    /**
     * 更新版本元数据
     */
    WorkflowVersionDTO updateVersionMeta(Long versionId, WorkflowVersionMetaUpdateDTO updateDTO);

    /**
     * 更新版本启停状态
     */
    WorkflowVersionDTO updateVersionActivation(Long versionId, WorkflowVersionActivationDTO activationDTO);

    /**
     * 删除工作流版本
     */
    void deleteVersion(Long versionId);

    /**
     * 获取待审核列表（仅超级管理员）
     */
    List<WorkflowApprovalDTO> getPendingApprovals();

    /**
     * 获取审核记录列表（仅超级管理员）
     */
    List<WorkflowApprovalDTO> getWorkflowApprovals();

    /**
     * 审核通过
     */
    void approveWorkflow(Long approvalId, String comment);

    /**
     * 审核拒绝
     */
    void rejectWorkflow(Long approvalId, String comment);

    /**
     * 删除单条审核记录
     */
    void deleteApproval(Long approvalId);

    /**
     * 清空全部审核记录
     */
    void clearAllApprovals();

    List<com.demand.system.module.workflow.dto.WorkflowValidationIssue> validateVersion(Long versionId);
}
