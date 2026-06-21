package com.demand.system.module.requirement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 需求待办任务表（物化视图）
 * 用于优化"我的待办"查询性能
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
     * 待办用户ID
     */
    private Long userId;

    /**
     * 待办来源类型
     */
    private String assigneeType;

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

    public RequirementPendingTask(Long requirementId, Long userId, String assigneeType,
                                  Long workflowInstanceId, String currentNodeId, String currentNodeName) {
        this.requirementId = requirementId;
        this.userId = userId;
        this.assigneeType = assigneeType;
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
