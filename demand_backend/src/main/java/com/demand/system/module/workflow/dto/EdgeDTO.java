package com.demand.system.module.workflow.dto;

import java.util.List;

public class EdgeDTO {

    private String source;

    private String target;

    private String label;

    private List<String> allowedRoles;

    private List<String> requiredFields;

    private String conditions;

    private Boolean defaultFlow;

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public List<String> getAllowedRoles() {
        return allowedRoles;
    }

    public void setAllowedRoles(List<String> allowedRoles) {
        this.allowedRoles = allowedRoles;
    }

    public List<String> getRequiredFields() {
        return requiredFields;
    }

    public void setRequiredFields(List<String> requiredFields) {
        this.requiredFields = requiredFields;
    }

    public String getConditions() {
        return conditions;
    }

    public void setConditions(String conditions) {
        this.conditions = conditions;
    }

    public Boolean getDefaultFlow() {
        return defaultFlow;
    }

    public void setDefaultFlow(Boolean defaultFlow) {
        this.defaultFlow = defaultFlow;
    }
}
