package com.demand.system.module.requirement.dto;

import java.util.List;

public class RequirementFormConfigDTO {

    private String defaultTypeCode;

    private String defaultTypeName;

    private String defaultTypeColor;

    private List<String> visibleFields;

    private List<String> requiredFields;

    /** 动态字段 schema（含每个字段的可见/可编辑权限）。 */
    private List<CustomFieldConfigDTO> dynamicFields;

    public String getDefaultTypeCode() {
        return defaultTypeCode;
    }

    public void setDefaultTypeCode(String defaultTypeCode) {
        this.defaultTypeCode = defaultTypeCode;
    }

    public String getDefaultTypeName() {
        return defaultTypeName;
    }

    public void setDefaultTypeName(String defaultTypeName) {
        this.defaultTypeName = defaultTypeName;
    }

    public String getDefaultTypeColor() {
        return defaultTypeColor;
    }

    public void setDefaultTypeColor(String defaultTypeColor) {
        this.defaultTypeColor = defaultTypeColor;
    }

    public List<String> getVisibleFields() {
        return visibleFields;
    }

    public void setVisibleFields(List<String> visibleFields) {
        this.visibleFields = visibleFields;
    }

    public List<String> getRequiredFields() {
        return requiredFields;
    }

    public void setRequiredFields(List<String> requiredFields) {
        this.requiredFields = requiredFields;
    }

    public List<CustomFieldConfigDTO> getDynamicFields() {
        return dynamicFields;
    }

    public void setDynamicFields(List<CustomFieldConfigDTO> dynamicFields) {
        this.dynamicFields = dynamicFields;
    }
}
