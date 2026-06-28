package com.demand.system.module.workflow.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 工作流配置校验报告。
 * 用于提交审核前汇总阻断错误、警告和优化建议，方便前端一次性展示问题清单。
 */
public class WorkflowValidationReport {

    private Long versionId;

    private String versionName;

    private String version;

    private LocalDateTime validatedAt;

    private List<WorkflowValidationIssue> issues = new ArrayList<>();

    private int errorCount;

    private int warningCount;

    private int infoCount;

    private boolean canSubmit;

    public Long getVersionId() {
        return versionId;
    }

    public void setVersionId(Long versionId) {
        this.versionId = versionId;
    }

    public String getVersionName() {
        return versionName;
    }

    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public LocalDateTime getValidatedAt() {
        return validatedAt;
    }

    public void setValidatedAt(LocalDateTime validatedAt) {
        this.validatedAt = validatedAt;
    }

    public List<WorkflowValidationIssue> getIssues() {
        return issues;
    }

    public void setIssues(List<WorkflowValidationIssue> issues) {
        this.issues = issues == null ? new ArrayList<>() : issues;
        computeSummary();
    }

    public int getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(int errorCount) {
        this.errorCount = errorCount;
    }

    public int getWarningCount() {
        return warningCount;
    }

    public void setWarningCount(int warningCount) {
        this.warningCount = warningCount;
    }

    public int getInfoCount() {
        return infoCount;
    }

    public void setInfoCount(int infoCount) {
        this.infoCount = infoCount;
    }

    public boolean isCanSubmit() {
        return canSubmit;
    }

    public void setCanSubmit(boolean canSubmit) {
        this.canSubmit = canSubmit;
    }

    public void computeSummary() {
        this.errorCount = 0;
        this.warningCount = 0;
        this.infoCount = 0;
        for (WorkflowValidationIssue issue : this.issues) {
            if (issue == null || issue.getSeverity() == null) {
                continue;
            }
            if ("error".equalsIgnoreCase(issue.getSeverity())) {
                this.errorCount++;
            } else if ("warning".equalsIgnoreCase(issue.getSeverity())) {
                this.warningCount++;
            } else if ("info".equalsIgnoreCase(issue.getSeverity())) {
                this.infoCount++;
            }
        }
        this.canSubmit = this.errorCount == 0;
    }
}
