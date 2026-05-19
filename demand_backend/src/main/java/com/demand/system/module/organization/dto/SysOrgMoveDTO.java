package com.demand.system.module.organization.dto;

import jakarta.validation.constraints.NotNull;

public class SysOrgMoveDTO {

    @NotNull(message = "节点ID不能为空")
    private Long id;

    private Long targetParentId;

    private Integer targetSortOrder;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTargetParentId() {
        return targetParentId;
    }

    public void setTargetParentId(Long targetParentId) {
        this.targetParentId = targetParentId;
    }

    public Integer getTargetSortOrder() {
        return targetSortOrder;
    }

    public void setTargetSortOrder(Integer targetSortOrder) {
        this.targetSortOrder = targetSortOrder;
    }
}
