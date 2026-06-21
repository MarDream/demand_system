package com.demand.system.module.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

/**
 * 工作流节点指派关联表
 * 用于替代workflow_nodes表中的逗号分隔/JSON数组字段
 * 提供高性能的索引查询支持
 */
@TableName("workflow_node_assignees")
public class WorkflowNodeAssignee {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 工作流版本ID
     */
    private Long workflowVersionId;

    /**
     * 节点ID
     */
    private String nodeId;

    /**
     * 指派类型：USER/ROLE/ROLE_GROUP/ORG/PREV_APPROVER/CREATOR
     */
    private String assigneeType;

    /**
     * 指派对象ID（用户ID/角色ID/组织ID）
     */
    private Long assigneeId;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getWorkflowVersionId() {
        return workflowVersionId;
    }

    public void setWorkflowVersionId(Long workflowVersionId) {
        this.workflowVersionId = workflowVersionId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getAssigneeType() {
        return assigneeType;
    }

    public void setAssigneeType(String assigneeType) {
        this.assigneeType = assigneeType;
    }

    public Long getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(Long assigneeId) {
        this.assigneeId = assigneeId;
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
