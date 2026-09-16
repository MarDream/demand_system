package com.demand.system.module.bitable.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 多维表格-数据表分组视图对象（树节点）
 */
public class BitableTableGroupVO {

    private Long id;

    private Long baseId;

    /**
     * 父分组ID，null=根层级
     */
    private Long parentId;

    private String name;

    private Integer sortOrder;

    private Long creatorId;

    /**
     * 该分组下直接挂载的数据表数量（不含子分组）
     */
    private Integer tableCount;

    /**
     * 该分组及其所有子孙分组下的数据表总数
     */
    private Integer totalTableCount;

    /**
     * 子分组列表
     */
    private List<BitableTableGroupVO> children = new ArrayList<>();

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBaseId() {
        return baseId;
    }

    public void setBaseId(Long baseId) {
        this.baseId = baseId;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Long getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(Long creatorId) {
        this.creatorId = creatorId;
    }

    public Integer getTableCount() {
        return tableCount;
    }

    public void setTableCount(Integer tableCount) {
        this.tableCount = tableCount;
    }

    public Integer getTotalTableCount() {
        return totalTableCount;
    }

    public void setTotalTableCount(Integer totalTableCount) {
        this.totalTableCount = totalTableCount;
    }

    public List<BitableTableGroupVO> getChildren() {
        return children;
    }

    public void setChildren(List<BitableTableGroupVO> children) {
        this.children = children;
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
