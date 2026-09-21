package com.demand.system.module.bitable.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * AI 自然语言生成筛选条件请求 DTO
 */
public class AiFilterGenerateRequest {

    @NotNull(message = "数据表ID不能为空")
    private Long tableId;

    @NotBlank(message = "筛选描述不能为空")
    @Size(max = 500, message = "筛选描述不能超过 500 字")
    private String text;

    public Long getTableId() {
        return tableId;
    }

    public void setTableId(Long tableId) {
        this.tableId = tableId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
