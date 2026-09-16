package com.demand.system.module.bitable.dto;

/**
 * 多维表格-更新数据表的DTO
 */
public class BitableTableUpdateDTO {

    private String name;

    private String description;

    private String icon;

    private Integer sortOrder;

    /**
     * 所属分组ID，非 null 时才更新（移出分组请用归组接口）
     */
    private Long groupId;

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

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }
}
