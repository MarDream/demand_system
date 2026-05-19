package com.demand.system.module.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import java.util.Objects;

@TableName(value = "workflow_node_permissions", autoResultMap = true)
public class WorkflowNodePermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workflowVersionId;

    private String nodeId;

    private String allowedRoles;

    private String allowedUsers;

    private String assigneeRule;

    private String visibleFields;

    private String editableFields;

    private String requiredFields;

    private String availableActions;

    private String actionConditions;

    private String notificationRules;

    private Integer timeoutHours;

    private String dataPermissions;

    private String attachmentPermissions;

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

    public String getAllowedRoles() {
        return allowedRoles;
    }

    public void setAllowedRoles(String allowedRoles) {
        this.allowedRoles = allowedRoles;
    }

    public String getAllowedUsers() {
        return allowedUsers;
    }

    public void setAllowedUsers(String allowedUsers) {
        this.allowedUsers = allowedUsers;
    }

    public String getAssigneeRule() {
        return assigneeRule;
    }

    public void setAssigneeRule(String assigneeRule) {
        this.assigneeRule = assigneeRule;
    }

    public String getVisibleFields() {
        return visibleFields;
    }

    public void setVisibleFields(String visibleFields) {
        this.visibleFields = visibleFields;
    }

    public String getEditableFields() {
        return editableFields;
    }

    public void setEditableFields(String editableFields) {
        this.editableFields = editableFields;
    }

    public String getRequiredFields() {
        return requiredFields;
    }

    public void setRequiredFields(String requiredFields) {
        this.requiredFields = requiredFields;
    }

    public String getAvailableActions() {
        return availableActions;
    }

    public void setAvailableActions(String availableActions) {
        this.availableActions = availableActions;
    }

    public String getActionConditions() {
        return actionConditions;
    }

    public void setActionConditions(String actionConditions) {
        this.actionConditions = actionConditions;
    }

    public String getNotificationRules() {
        return notificationRules;
    }

    public void setNotificationRules(String notificationRules) {
        this.notificationRules = notificationRules;
    }

    public Integer getTimeoutHours() {
        return timeoutHours;
    }

    public void setTimeoutHours(Integer timeoutHours) {
        this.timeoutHours = timeoutHours;
    }

    public String getDataPermissions() {
        return dataPermissions;
    }

    public void setDataPermissions(String dataPermissions) {
        this.dataPermissions = dataPermissions;
    }

    public String getAttachmentPermissions() {
        return attachmentPermissions;
    }

    public void setAttachmentPermissions(String attachmentPermissions) {
        this.attachmentPermissions = attachmentPermissions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkflowNodePermission that = (WorkflowNodePermission) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
