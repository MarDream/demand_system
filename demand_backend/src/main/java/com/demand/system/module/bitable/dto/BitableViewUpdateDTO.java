package com.demand.system.module.bitable.dto;

/**
 * 多维表格-更新视图的DTO
 */
public class BitableViewUpdateDTO {

    private String name;

    private String viewType;

    private String sortConfig;

    private String filterConfig;

    private String groupConfig;

    private String columnConfig;

    private String colorConfig;

    private Integer sortOrder;

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

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
