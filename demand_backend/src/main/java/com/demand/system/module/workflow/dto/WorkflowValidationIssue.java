package com.demand.system.module.workflow.dto;

public class WorkflowValidationIssue {

    private String path;
    private String message;
    private String severity;
    private String ruleCode;
    private String fieldPath;
    private String suggestion;
    private boolean blocking;

    public WorkflowValidationIssue() {
    }

    public WorkflowValidationIssue(String path, String message, String severity) {
        this.path = path;
        this.message = message;
        this.severity = severity;
        this.ruleCode = path;
        this.fieldPath = path;
        this.blocking = "error".equalsIgnoreCase(severity);
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
        this.blocking = "error".equalsIgnoreCase(severity);
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public String getFieldPath() {
        return fieldPath;
    }

    public void setFieldPath(String fieldPath) {
        this.fieldPath = fieldPath;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    public boolean isBlocking() {
        return blocking;
    }

    public void setBlocking(boolean blocking) {
        this.blocking = blocking;
    }
}