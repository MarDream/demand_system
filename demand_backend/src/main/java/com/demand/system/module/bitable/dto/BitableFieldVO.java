package com.demand.system.module.bitable.dto;

import java.time.LocalDateTime;

/**
 * 多维表格-字段视图对象
 */
public class BitableFieldVO {

    private Long id;

    private Long tableId;

    private String name;

    private String fieldType;

    private Object config;

    private Integer required;

    private String aiPrompt;

    private Integer isAiField;

    private Integer sortOrder;

    private Integer width;

    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTableId() {
        return tableId;
    }

    public void setTableId(Long tableId) {
        this.tableId = tableId;
    }

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

    public Object getConfig() {
        return config;
    }

    public void setConfig(Object config) {
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

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
