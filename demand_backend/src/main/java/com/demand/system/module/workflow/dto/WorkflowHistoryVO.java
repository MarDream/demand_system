package com.demand.system.module.workflow.dto;

import java.time.LocalDateTime;

public class WorkflowHistoryVO {
    private Long id;
    private Long workflowVersionId;
    private String operatorName;
    private String action;
    private String changeSummary;
    private String changeLog;
    private String versionSnapshot;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getWorkflowVersionId() { return workflowVersionId; }
    public void setWorkflowVersionId(Long workflowVersionId) { this.workflowVersionId = workflowVersionId; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getChangeSummary() { return changeSummary; }
    public void setChangeSummary(String changeSummary) { this.changeSummary = changeSummary; }
    public String getChangeLog() { return changeLog; }
    public void setChangeLog(String changeLog) { this.changeLog = changeLog; }
    public String getVersionSnapshot() { return versionSnapshot; }
    public void setVersionSnapshot(String versionSnapshot) { this.versionSnapshot = versionSnapshot; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
