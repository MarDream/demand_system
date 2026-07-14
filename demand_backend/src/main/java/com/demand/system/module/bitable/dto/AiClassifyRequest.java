package com.demand.system.module.bitable.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * AI 自动分类请求 DTO
 */
public class AiClassifyRequest {

    @NotNull(message = "数据表ID不能为空")
    private Long tableId;

    @NotNull(message = "源字段ID不能为空")
    private Long sourceFieldId;

    @NotBlank(message = "目标字段名不能为空")
    private String targetFieldName;

    public Long getTableId() {
        return tableId;
    }

    public void setTableId(Long tableId) {
        this.tableId = tableId;
    }

    public Long getSourceFieldId() {
        return sourceFieldId;
    }

    public void setSourceFieldId(Long sourceFieldId) {
        this.sourceFieldId = sourceFieldId;
    }

    public String getTargetFieldName() {
        return targetFieldName;
    }

    public void setTargetFieldName(String targetFieldName) {
        this.targetFieldName = targetFieldName;
    }
}