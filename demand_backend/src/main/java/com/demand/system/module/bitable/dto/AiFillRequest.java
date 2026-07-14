package com.demand.system.module.bitable.dto;

import jakarta.validation.constraints.NotNull;

/**
 * AI 智能填充请求 DTO
 */
public class AiFillRequest {

    @NotNull(message = "数据表ID不能为空")
    private Long tableId;

    private Long recordId; // 单条填充时指定

    @NotNull(message = "字段ID不能为空")
    private Long fieldId;

    public Long getTableId() {
        return tableId;
    }

    public void setTableId(Long tableId) {
        this.tableId = tableId;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public Long getFieldId() {
        return fieldId;
    }

    public void setFieldId(Long fieldId) {
        this.fieldId = fieldId;
    }
}