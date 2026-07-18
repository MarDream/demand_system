package com.demand.system.module.bitable.dto;

/**
 * 多维表格-更新视图的DTO
 */
public class BitableViewUpdateDTO {

    private String name;

    private String viewType;

    private Object sortConfig;

    private Object filterConfig;

    private Object groupConfig;

    private Object columnConfig;

    private Object colorConfig;

    private Object config;

    private Integer sortOrder;

    private Integer version;

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

    public Object getSortConfig() {
        return sortConfig;
    }

    public void setSortConfig(Object sortConfig) {
        this.sortConfig = sortConfig;
    }

    public Object getFilterConfig() {
        return filterConfig;
    }

    public void setFilterConfig(Object filterConfig) {
        this.filterConfig = filterConfig;
    }

    public Object getGroupConfig() {
        return groupConfig;
    }

    public void setGroupConfig(Object groupConfig) {
        this.groupConfig = groupConfig;
    }

    public Object getColumnConfig() {
        return columnConfig;
    }

    public void setColumnConfig(Object columnConfig) {
        this.columnConfig = columnConfig;
    }

    public Object getColorConfig() {
        return colorConfig;
    }

    public void setColorConfig(Object colorConfig) {
        this.colorConfig = colorConfig;
    }

    public Object getConfig() {
        return config;
    }

    public void setConfig(Object config) {
        this.config = config;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
