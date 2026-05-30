package com.demand.system.module.workflow.dto;

import java.time.LocalDateTime;

public class ParallelBranchVO {

    private Long id;
    private Long instanceId;
    private String parallelNodeId;
    private String branchNodeId;
    private String branchName;
    private String currentNodeId;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(Long instanceId) {
        this.instanceId = instanceId;
    }

    public String getParallelNodeId() {
        return parallelNodeId;
    }

    public void setParallelNodeId(String parallelNodeId) {
        this.parallelNodeId = parallelNodeId;
    }

    public String getBranchNodeId() {
        return branchNodeId;
    }

    public void setBranchNodeId(String branchNodeId) {
        this.branchNodeId = branchNodeId;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public String getCurrentNodeId() {
        return currentNodeId;
    }

    public void setCurrentNodeId(String currentNodeId) {
        this.currentNodeId = currentNodeId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
