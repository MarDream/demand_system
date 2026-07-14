package com.demand.system.module.bitable.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 多维表格-创建视图的DTO
 */
public class BitableViewCreateDTO {

    @NotBlank(message = "视图名称不能为空")
    private String name;

    @NotBlank(message = "视图类型不能为空")
    private String viewType;

    private String sortConfig;

    private String filterConfig;

    private String groupConfig;

    private String columnConfig;

    private String colorConfig;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getViewType() {
        return viewType;
    }

    public void setViewType(String viewType) {
        this.viewType = viewType;
    }

    public String getSortConfig() {
        return sortConfig;
    }

    public void setSortConfig(String sortConfig) {
        this.sortConfig = sortConfig;
    }

    public String getFilterConfig() {
        return filterConfig;
    }

    public void setFilterConfig(String filterConfig) {
        this.filterConfig = filterConfig;
    }

    public String getGroupConfig() {
        return groupConfig;
    }

    public void setGroupConfig(String groupConfig) {
        this.groupConfig = groupConfig;
    }

    public String getColumnConfig() {
        return columnConfig;
    }

    public void setColumnConfig(String columnConfig) {
        this.columnConfig = columnConfig;
    }

    public String getColorConfig() {
        return colorConfig;
    }

    public void setColorConfig(String colorConfig) {
        this.colorConfig = colorConfig;
    }
}
