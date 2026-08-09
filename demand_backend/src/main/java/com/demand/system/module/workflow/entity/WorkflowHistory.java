package com.demand.system.module.workflow.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import java.util.Objects;

@TableName("workflow_history")
public class WorkflowHistory {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workflowVersionId;
    private Long projectId;
    private Long operatorId;
    private String action;
    private String changeSummary;
    private String changeLog;
    private String versionSnapshot;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getWorkflowVersionId() { return workflowVersionId; }
    public void setWorkflowVersionId(Long workflowVersionId) { this.workflowVersionId = workflowVersionId; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkflowHistory that = (WorkflowHistory) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
