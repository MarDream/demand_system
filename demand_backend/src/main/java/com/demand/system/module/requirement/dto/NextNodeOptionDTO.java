package com.demand.system.module.requirement.dto;

public class NextNodeOptionDTO {

    private String nodeId;

    private String nodeName;

    private String bindStatusCode;

    private String bindStatusName;

    private Boolean projectRequired;

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
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
