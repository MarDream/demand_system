package com.demand.system.module.workflow.dto;

public class WorkflowValidationIssue {

    private String path;
    private String message;
    private String severity;

    public WorkflowValidationIssue() {
    }

    public WorkflowValidationIssue(String path, String message, String severity) {
        this.path = path;
        this.message = message;
        this.severity = severity;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }
}
