package com.demand.system.module.workflow.service;

import com.demand.system.module.workflow.dto.WorkflowApprovalDTO;
import com.demand.system.module.workflow.dto.WorkflowConfigDTO;
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
    void saveWorkflowConfig(Long projectId, WorkflowConfigDTO configDTO);

    /**
     * 提交审核
     */
    void submitForApproval(Long projectId);

    /**
     * 获取历史版本列表
     */
    List<WorkflowVersionDTO> getVersionHistory(Long projectId);

    /**
     * 获取指定版本配置
     */
    WorkflowVersionDTO getVersionConfig(Long versionId);

    /**
     * 获取待审核列表（仅超级管理员）
     */
    List<WorkflowApprovalDTO> getPendingApprovals();

    /**
     * 审核通过
     */
    void approveWorkflow(Long approvalId, String comment);

    /**
     * 审核拒绝
     */
    void rejectWorkflow(Long approvalId, String comment);
}
