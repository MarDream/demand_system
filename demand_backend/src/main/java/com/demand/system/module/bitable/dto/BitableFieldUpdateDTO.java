package com.demand.system.module.bitable.dto;

/**
 * 多维表格-更新字段的DTO
 */
public class BitableFieldUpdateDTO {

    private String name;

    private String fieldType;

    private String config;

    private Integer required;

    private String aiPrompt;

    private Integer isAiField;

    private Integer width;

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
}
