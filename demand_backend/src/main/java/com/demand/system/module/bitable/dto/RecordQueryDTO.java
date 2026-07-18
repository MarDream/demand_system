package com.demand.system.module.bitable.dto;

import java.util.List;

/**
 * 记录查询参数DTO（支持筛选、排序、分组）
 */
public class RecordQueryDTO {

    /** 页码，默认1 */
    private Integer pageNum = 1;

    /** 每页大小，默认100 */
    private Integer pageSize = 100;

    /**
     * 筛选配置（来自视图或临时传入），格式与 filter_config 一致。
     * 支持两种格式：
     * 1. 简单数组：[{fieldId, operator, value, conjunction}, ...]
     * 2. 嵌套逻辑：{logic: "and"/"or", rules: [...]}
     */
    private Object filterConfig;

    /**
     * 排序配置，格式：[{fieldId: 101, direction: "asc"}, ...]
     * 多个排序项按优先级从高到低排列
     */
    private Object sortConfig;

    /** 分组字段ID */
    private Long groupByFieldId;

    /**
     * 视图ID（如果传入，自动从视图配置加载筛选/排序/分组，
     * 与直接传入的 filterConfig/sortConfig/groupByFieldId 合并，直接传入优先）
     */
    private Long viewId;

    public Integer getPageNum() {
        return pageNum;
    }

    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public Object getFilterConfig() {
        return filterConfig;
    }

    public void setFilterConfig(Object filterConfig) {
        this.filterConfig = filterConfig;
    }

    public Object getSortConfig() {
        return sortConfig;
    }

    public void setSortConfig(Object sortConfig) {
        this.sortConfig = sortConfig;
    }

    public Long getGroupByFieldId() {
        return groupByFieldId;
    }

    public void setGroupByFieldId(Long groupByFieldId) {
        this.groupByFieldId = groupByFieldId;
    }

    public Long getViewId() {
        return viewId;
    }

    public void setViewId(Long viewId) {
        this.viewId = viewId;
    }
}
