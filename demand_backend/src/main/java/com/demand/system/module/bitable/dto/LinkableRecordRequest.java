package com.demand.system.module.bitable.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 创建关联记录请求DTO
 */
public class LinkableRecordRequest {

    @NotNull(message = "目标表ID不能为空")
    private Long targetTableId;

    private String keyword;

    private Integer pageSize = 50;

    public Long getTargetTableId() {
        return targetTableId;
    }

    public void setTargetTableId(Long targetTableId) {
        this.targetTableId = targetTableId;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }
}