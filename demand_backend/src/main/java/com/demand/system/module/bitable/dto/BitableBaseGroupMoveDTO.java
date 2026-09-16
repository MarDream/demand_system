package com.demand.system.module.bitable.dto;

/**
 * 多维表格-移动 Base 分组的DTO
 * <p>
 * 用于拖拽调整分组位置：变更父级和/或同级排序。
 * {@code parentId} 为 null 表示移动到根层级；两个字段都可为空，为空表示不修改。
 */
public class BitableBaseGroupMoveDTO {

    /**
     * 目标父分组ID，null=根层级
     */
    private Long parentId;

    /**
     * 目标排序号，null=追加到末尾
     */
    private Integer sortOrder;

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
