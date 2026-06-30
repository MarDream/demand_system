package com.demand.system.module.workflow.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 创建迁移计划请求
 */
public class CreateMigrationPlanRequest {

    @NotNull(message = "源版本ID不能为空")
    private Long fromVersionId;

    @NotNull(message = "目标版本ID不能为空")
    private Long toVersionId;

    private String remark;

    public Long getFromVersionId() { return fromVersionId; }
    public void setFromVersionId(Long fromVersionId) { this.fromVersionId = fromVersionId; }

    public Long getToVersionId() { return toVersionId; }
    public void setToVersionId(Long toVersionId) { this.toVersionId = toVersionId; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
