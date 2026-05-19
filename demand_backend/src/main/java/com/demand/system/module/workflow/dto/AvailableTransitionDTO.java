package com.demand.system.module.workflow.dto;

public class AvailableTransitionDTO {

    private String toNodeId;

    private String toNodeName;

    private String label;

    private String bindStatusCode;

    private String bindStatusName;

    private Boolean projectRequired;

    public String getToNodeId() {
        return toNodeId;
    }

    public void setToNodeId(String toNodeId) {
        this.toNodeId = toNodeId;
    }

    public String getToNodeName() {
        return toNodeName;
    }

    public void setToNodeName(String toNodeName) {
        this.toNodeName = toNodeName;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getBindStatusCode() {
        return bindStatusCode;
    }

    public void setBindStatusCode(String bindStatusCode) {
        this.bindStatusCode = bindStatusCode;
    }

    public String getBindStatusName() {
        return bindStatusName;
    }

    public void setBindStatusName(String bindStatusName) {
        this.bindStatusName = bindStatusName;
    }

    public Boolean getProjectRequired() {
        return projectRequired;
    }

    public void setProjectRequired(Boolean projectRequired) {
        this.projectRequired = projectRequired;
    }
}
