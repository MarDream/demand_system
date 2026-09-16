package com.demand.system.module.llm.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * LLM 功能点模型应用分组视图对象（树节点）
 */
public class LlmApplicationGroupVO {

    private Long id;

    /**
     * 父分组ID，null=根层级
     */
    private Long parentId;

    private String name;

    private Integer sortOrder;

    /**
     * 该分组下直接挂载的功能点数量（不含子分组）
     */
    private Integer applicationCount;

    /**
     * 该分组及其所有子孙分组下的功能点总数
     */
    private Integer totalApplicationCount;

    /**
     * 子分组列表
     */
    private List<LlmApplicationGroupVO> children = new ArrayList<>();

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Integer getApplicationCount() { return applicationCount; }
    public void setApplicationCount(Integer applicationCount) { this.applicationCount = applicationCount; }
    public Integer getTotalApplicationCount() { return totalApplicationCount; }
    public void setTotalApplicationCount(Integer totalApplicationCount) { this.totalApplicationCount = totalApplicationCount; }
    public List<LlmApplicationGroupVO> getChildren() { return children; }
    public void setChildren(List<LlmApplicationGroupVO> children) { this.children = children; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
