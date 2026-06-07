package com.demand.system.module.workflow.dto;

import java.util.List;

public class AvailableTransitionDTO {

    private String toNodeId;

    private String toNodeName;

    private String label;

    private String bindStatusCode;

    private String bindStatusName;

    private Boolean projectRequired;

    private String assigneeType;

    private String assigneeTypeName;

    private String assigneeDisplayName;

    private List<AssigneeCandidateDTO> assigneeCandidates;

    private Long defaultAssigneeId;

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

    public String getAssigneeType() {
        return assigneeType;
    }

    public void setAssigneeType(String assigneeType) {
        this.assigneeType = assigneeType;
    }

    public String getAssigneeTypeName() {
        return assigneeTypeName;
    }

    public void setAssigneeTypeName(String assigneeTypeName) {
        this.assigneeTypeName = assigneeTypeName;
    }

    public String getAssigneeDisplayName() {
        return assigneeDisplayName;
    }

    public void setAssigneeDisplayName(String assigneeDisplayName) {
        this.assigneeDisplayName = assigneeDisplayName;
    }

    public List<AssigneeCandidateDTO> getAssigneeCandidates() {
        return assigneeCandidates;
    }

    public void setAssigneeCandidates(List<AssigneeCandidateDTO> assigneeCandidates) {
        this.assigneeCandidates = assigneeCandidates;
    }

    public Long getDefaultAssigneeId() {
        return defaultAssigneeId;
    }

    public void setDefaultAssigneeId(Long defaultAssigneeId) {
        this.defaultAssigneeId = defaultAssigneeId;
    }
}
