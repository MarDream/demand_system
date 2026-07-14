package com.demand.system.module.bitable.dto;

/**
 * 多维表格-更新Base的DTO
 */
public class BitableBaseUpdateDTO {

    private String name;

    private String description;

    private String icon;

    private String coverColor;

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
}
