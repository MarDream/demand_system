package com.demand.system.module.requirement.dto;

import java.util.List;

public class RequirementSubmitDTO {

    private Integer version;

    private String nextNodeId;

    private Long projectId;

    private String comment;

    /**
     * 提交前是否真实保存过草稿（用户点过「保存草稿」或从草稿箱打开提交）。
     * 创建页直接提交时前端总会先建草稿再提交，该场景不算有草稿阶段，
     * 后端据此决定是否补插「草稿 → 开始」流转记录。缺省按 false 处理。
     */
    private Boolean draftSaved;

    private List<CustomFieldValueDTO> customFields;

    public Integer getVersion() {
        return version;
    }

    public Boolean getDraftSaved() {
        return draftSaved;
    }

    public void setDraftSaved(Boolean draftSaved) {
        this.draftSaved = draftSaved;
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
