package com.demand.system.module.bitable.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 多维表格-创建数据表的DTO
 */
public class BitableTableCreateDTO {

    @NotBlank(message = "数据表名称不能为空")
    private String name;

    private String description;

    private String icon;

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
}
