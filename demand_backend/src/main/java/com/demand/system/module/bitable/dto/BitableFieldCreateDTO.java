package com.demand.system.module.bitable.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 多维表格-创建字段的DTO
 */
public class BitableFieldCreateDTO {

    @NotBlank(message = "字段名称不能为空")
    private String name;

    @NotBlank(message = "字段类型不能为空")
    private String fieldType;

    private String config;

    private Integer required = 0;

    private String aiPrompt;

    private Integer isAiField = 0;

    private Integer width = 150;

    private String description;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFieldType() {
        return fieldType;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }

    public String getConfig() {
        return config;
    }

    public void setConfig(String config) {
        this.config = config;
    }

    public Integer getRequired() {
        return required;
    }

    public void setRequired(Integer required) {
        this.required = required;
    }

    public String getAiPrompt() {
        return aiPrompt;
    }

    public void setAiPrompt(String aiPrompt) {
        this.aiPrompt = aiPrompt;
    }

    public Integer getIsAiField() {
        return isAiField;
    }

    public void setIsAiField(Integer isAiField) {
        this.isAiField = isAiField;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
