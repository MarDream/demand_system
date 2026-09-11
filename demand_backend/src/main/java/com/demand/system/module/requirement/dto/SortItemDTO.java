package com.demand.system.module.requirement.dto;

/** 排序项：ID + 新的排序值。 */
public class SortItemDTO {

    private Long id;

    private Integer sortOrder;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
