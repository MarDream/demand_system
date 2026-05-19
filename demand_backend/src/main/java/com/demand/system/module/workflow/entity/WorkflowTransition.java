package com.demand.system.module.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import java.util.Objects;

@TableName(value = "workflow_transitions", autoResultMap = true)
public class WorkflowTransition {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private Long fromStateId;

    private Long toStateId;

    private String allowedRoles;

    private String requiredFields;

    private String conditions;

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

    public Long getFromStateId() {
        return fromStateId;
    }

    public void setFromStateId(Long fromStateId) {
        this.fromStateId = fromStateId;
    }

    public Long getToStateId() {
        return toStateId;
    }

    public void setToStateId(Long toStateId) {
        this.toStateId = toStateId;
    }

    public String getAllowedRoles() {
        return allowedRoles;
    }

    public void setAllowedRoles(String allowedRoles) {
        this.allowedRoles = allowedRoles;
    }

    public String getRequiredFields() {
        return requiredFields;
    }

    public void setRequiredFields(String requiredFields) {
        this.requiredFields = requiredFields;
    }

    public String getConditions() {
        return conditions;
    }

    public void setConditions(String conditions) {
        this.conditions = conditions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkflowTransition that = (WorkflowTransition) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
