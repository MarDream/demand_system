package com.demand.system.module.requirement.dto;

import java.util.Map;

public class RequirementTemplateVO {

    private Long id;
    private String requirementTypeCode;
    private String templateName;
    private Map<String, Object> templateContent;
    private Integer isActive;
    private Integer isDefault;
    private Integer sortOrder;
    private Integer creatorId;
    private String requirementTypeName;

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

    public Integer getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Integer isDefault) {
        this.isDefault = isDefault;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getRequirementTypeName() {
        return requirementTypeName;
    }

    public void setRequirementTypeName(String requirementTypeName) {
        this.requirementTypeName = requirementTypeName;
    }
}
