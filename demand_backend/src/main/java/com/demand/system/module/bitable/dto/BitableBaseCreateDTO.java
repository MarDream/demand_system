package com.demand.system.module.bitable.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 多维表格-创建Base的DTO
 */
public class BitableBaseCreateDTO {

    @NotBlank(message = "表格名称不能为空")
    private String name;

    private String description;

    private String icon;

    private String coverColor;

    private Long projectId;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getCoverColor() {
        return coverColor;
    }

    public void setCoverColor(String coverColor) {
        this.coverColor = coverColor;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }
}
