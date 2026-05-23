package com.demand.system.module.workflow.dto;

import java.time.LocalDateTime;

public class WorkflowVersionDTO {

    private Long id;

    private Long projectId;

    private String version;

    private String name;

    private Integer isActive;

    private String activationStatus;

    private String runtimeHash;

    private LocalDateTime activatedAt;

    private Long creatorId;

    private String creatorName;

    private LocalDateTime createdAt;

    private String latestApprovalStatus;

    private String latestApprovalComment;

    private LocalDateTime latestSubmittedAt;

    private LocalDateTime latestApprovedAt;

    private WorkflowConfigDTO config;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getIsActive() {
        return isActive;
    }

    public void setIsActive(Integer isActive) {
        this.isActive = isActive;
    }

    public String getActivationStatus() {
        return activationStatus;
    }

    public void setActivationStatus(String activationStatus) {
        this.activationStatus = activationStatus;
    }

    public String getRuntimeHash() {
        return runtimeHash;
    }

    public void setRuntimeHash(String runtimeHash) {
        this.runtimeHash = runtimeHash;
    }

    public LocalDateTime getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(LocalDateTime activatedAt) {
        this.activatedAt = activatedAt;
    }

    public Long getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(Long creatorId) {
        this.creatorId = creatorId;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public void setCreatorName(String creatorName) {
        this.creatorName = creatorName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getLatestApprovalStatus() {
        return latestApprovalStatus;
    }

    public void setLatestApprovalStatus(String latestApprovalStatus) {
        this.latestApprovalStatus = latestApprovalStatus;
    }

    public String getLatestApprovalComment() {
        return latestApprovalComment;
    }

    public void setLatestApprovalComment(String latestApprovalComment) {
        this.latestApprovalComment = latestApprovalComment;
    }

    public LocalDateTime getLatestSubmittedAt() {
        return latestSubmittedAt;
    }

    public void setLatestSubmittedAt(LocalDateTime latestSubmittedAt) {
        this.latestSubmittedAt = latestSubmittedAt;
    }

    public LocalDateTime getLatestApprovedAt() {
        return latestApprovedAt;
    }

    public void setLatestApprovedAt(LocalDateTime latestApprovedAt) {
        this.latestApprovedAt = latestApprovedAt;
    }

    public WorkflowConfigDTO getConfig() {
        return config;
    }

    public void setConfig(WorkflowConfigDTO config) {
        this.config = config;
    }
}
