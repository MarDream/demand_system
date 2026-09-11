package com.demand.system.module.requirement.dto;

import java.util.List;

public class RequirementSubmitDTO {

    private Integer version;

    private String nextNodeId;

    private Long projectId;

    private String comment;

    private List<CustomFieldValueDTO> customFields;

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getNextNodeId() {
        return nextNodeId;
    }

    public void setNextNodeId(String nextNodeId) {
        this.nextNodeId = nextNodeId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public List<CustomFieldValueDTO> getCustomFields() {
        return customFields;
    }

    public void setCustomFields(List<CustomFieldValueDTO> customFields) {
        this.customFields = customFields;
    }
}
