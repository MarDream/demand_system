package com.demand.system.module.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 工作流审计日志实体
 */
@TableName(value = "workflow_audit_logs", autoResultMap = true)
public class WorkflowAuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workflowVersionId;

    private String action;

    private Long operatorId;

    private String operatorName;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> details;

    private String ipAddress;

    private String userAgent;

    private LocalDateTime createdAt;

    // Constructors

    public WorkflowAuditLog() {
    }

    public WorkflowAuditLog(Long workflowVersionId, String action, Long operatorId, String operatorName) {
        this.workflowVersionId = workflowVersionId;
        this.action = action;
        this.operatorId = operatorId;
        this.operatorName = operatorName;
    }

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

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
