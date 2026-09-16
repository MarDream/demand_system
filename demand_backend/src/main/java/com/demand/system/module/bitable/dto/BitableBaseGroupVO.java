package com.demand.system.module.bitable.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 多维表格-Base分组视图对象（树节点）
 */
public class BitableBaseGroupVO {

    private Long id;

    /**
     * 父分组ID，null=根层级
     */
    private Long parentId;

    private String name;

    private Integer sortOrder;

    private Long creatorId;

    /**
     * 该分组下直接挂载的 Base 数量（不含子分组）
     */
    private Integer baseCount;

    /**
     * 该分组及其所有子孙分组下的 Base 总数
     */
    private Integer totalBaseCount;

    /**
     * 子分组列表
     */
    private List<BitableBaseGroupVO> children = new ArrayList<>();

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Integer getBaseCount() {
        return baseCount;
    }

    public void setBaseCount(Integer baseCount) {
        this.baseCount = baseCount;
    }

    public Integer getTotalBaseCount() {
        return totalBaseCount;
    }

    public void setTotalBaseCount(Integer totalBaseCount) {
        this.totalBaseCount = totalBaseCount;
    }

    public List<BitableBaseGroupVO> getChildren() {
        return children;
    }

    public void setChildren(List<BitableBaseGroupVO> children) {
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
