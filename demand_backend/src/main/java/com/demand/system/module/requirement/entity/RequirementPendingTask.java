package com.demand.system.module.requirement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 需求待办任务表（物化视图）
 * 用于优化"我的待办"查询性能
 *
 * 待办来源类型（assigneeType）对应的存储策略：
 * - SPECIFIED_USER: 有选中人时存 user_id，否则存所有候选人 user_id
 * - SPECIFIED_ROLE: 存 role_id
 * - SPECIFIED_ROLE_GROUP: 存 role_group_id
 * - SPECIFIED_ORG: 存 org_id
 * - CREATOR/PREV_APPROVER: 存 user_id
 */
@TableName("requirement_pending_tasks")
public class RequirementPendingTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 需求ID
     */
    private Long requirementId;

    /**
     * 任务类型：APPROVAL=可操作审批待办，CC_READ_ONLY=只读抄送查阅待办
     */
    private String taskType = "APPROVAL";

    /**
     * 待办用户ID
     * - SPECIFIED_USER/SPECIFIED_USER 有选中人时：存选中用户的ID
     * - SPECIFIED_USER 无选中人时：存所有候选人的 user_id
     * - CREATOR/PREV_APPROVER：存用户ID
     * - 其他类型（ROLE/ROLE_GROUP/ORG）：为 null
     */
    private Long userId;

    /**
     * 待办来源类型：SPECIFIED_USER | SPECIFIED_ROLE | SPECIFIED_ROLE_GROUP | SPECIFIED_ORG | CREATOR | PREV_APPROVER
     */
    private String assigneeType;

    /**
     * 角色ID（当assigneeType=SPECIFIED_ROLE时）
     */
    private Long roleId;

    /**
     * 角色组ID（当assigneeType=SPECIFIED_ROLE_GROUP时）
     */
    private Long roleGroupId;

    /**
     * 组织ID（当assigneeType=SPECIFIED_ORG时）
     */
    private Long orgId;

    /**
     * 工作流实例ID
     */
    private Long workflowInstanceId;

    /**
     * 当前节点ID
     */
    private String currentNodeId;

    /**
     * 当前节点名称
     */
    private String currentNodeName;

    /**
     * 待办创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    public RequirementPendingTask() {
    }

    /**
     * 构造方法（兼容原有调用）
     */
    public RequirementPendingTask(Long requirementId, Long userId, String assigneeType,
                                  Long workflowInstanceId, String currentNodeId, String currentNodeName) {
        this.requirementId = requirementId;
        this.userId = userId;
        this.assigneeType = assigneeType;
        this.workflowInstanceId = workflowInstanceId;
        this.currentNodeId = currentNodeId;
        this.currentNodeName = currentNodeName;
    }

    /**
     * 构造方法（用于存角色/角色组/组织范围）
     */
    public RequirementPendingTask(Long requirementId, String assigneeType,
                                  Long roleId, Long roleGroupId, Long orgId,
                                  Long workflowInstanceId, String currentNodeId, String currentNodeName) {
        this.requirementId = requirementId;
        this.assigneeType = assigneeType;
        this.roleId = roleId;
        this.roleGroupId = roleGroupId;
        this.orgId = orgId;
        this.workflowInstanceId = workflowInstanceId;
        this.currentNodeId = currentNodeId;
        this.currentNodeName = currentNodeName;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRequirementId() {
        return requirementId;
    }

    public void setRequirementId(Long requirementId) {
        this.requirementId = requirementId;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getAssigneeType() {
        return assigneeType;
    }

    public void setAssigneeType(String assigneeType) {
        this.assigneeType = assigneeType;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public Long getRoleGroupId() {
        return roleGroupId;
    }

    public void setRoleGroupId(Long roleGroupId) {
        this.roleGroupId = roleGroupId;
    }

    public Long getOrgId() {
        return orgId;
    }

    public void setOrgId(Long orgId) {
        this.orgId = orgId;
    }

    public Long getWorkflowInstanceId() {
        return workflowInstanceId;
    }

    public void setWorkflowInstanceId(Long workflowInstanceId) {
        this.workflowInstanceId = workflowInstanceId;
    }

    public String getCurrentNodeId() {
        return currentNodeId;
    }

    public void setCurrentNodeId(String currentNodeId) {
        this.currentNodeId = currentNodeId;
    }

    public String getCurrentNodeName() {
        return currentNodeName;
    }

    public void setCurrentNodeName(String currentNodeName) {
        this.currentNodeName = currentNodeName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
