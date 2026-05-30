package com.demand.system.module.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import java.time.LocalDateTime;
import java.util.Objects;

@TableName(value = "workflow_instances", autoResultMap = true)
public class WorkflowInstance {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long requirementId;

    private Long workflowVersionId;

    private String currentNodeId;

    private String previousNodeId;

    private String status;

    private Integer lockVersion;

    private String parallelNodeId;

    private Long activeParallelBranchId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

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

    public Long getWorkflowVersionId() {
        return workflowVersionId;
    }

    public void setWorkflowVersionId(Long workflowVersionId) {
        this.workflowVersionId = workflowVersionId;
    }

    public String getCurrentNodeId() {
        return currentNodeId;
    }

    public void setCurrentNodeId(String currentNodeId) {
        this.currentNodeId = currentNodeId;
    }

    public String getPreviousNodeId() {
        return previousNodeId;
    }

    public void setPreviousNodeId(String previousNodeId) {
        this.previousNodeId = previousNodeId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getLockVersion() {
        return lockVersion;
    }

    public void setLockVersion(Integer lockVersion) {
        this.lockVersion = lockVersion;
    }

    public String getParallelNodeId() {
        return parallelNodeId;
    }

    public void setParallelNodeId(String parallelNodeId) {
        this.parallelNodeId = parallelNodeId;
    }

    public Long getActiveParallelBranchId() {
        return activeParallelBranchId;
    }

    public void setActiveParallelBranchId(Long activeParallelBranchId) {
        this.activeParallelBranchId = activeParallelBranchId;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkflowInstance that = (WorkflowInstance) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
