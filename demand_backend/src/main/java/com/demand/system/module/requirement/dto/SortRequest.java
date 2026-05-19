package com.demand.system.module.requirement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "排序请求")
public class SortRequest {

    @NotNull(message = "ID不能为空")
    @Schema(description = "记录ID")
    private Long id;

    @NotNull(message = "排序值不能为空")
    @Schema(description = "排序值")
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
