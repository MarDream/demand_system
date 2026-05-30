package com.demand.system.module.requirement.dto;

import java.util.Map;

public class RequirementTemplateVO {

    private Long id;
    private String requirementTypeCode;
    private String templateName;
    private Map<String, Object> templateContent;
    private Integer isActive;
    private Integer creatorId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRequirementTypeCode() {
        return requirementTypeCode;
    }

    public void setRequirementTypeCode(String requirementTypeCode) {
        this.requirementTypeCode = requirementTypeCode;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public Map<String, Object> getTemplateContent() {
        return templateContent;
    }

    public void setTemplateContent(Map<String, Object> templateContent) {
        this.templateContent = templateContent;
    }

    public Integer getIsActive() {
        return isActive;
    }

    public void setIsActive(Integer isActive) {
        this.isActive = isActive;
    }

    public Integer getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(Integer creatorId) {
        this.creatorId = creatorId;
    }
}
