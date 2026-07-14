package com.demand.system.module.bitable.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * AI 对话式查询请求 DTO
 */
public class AiQueryRequest {

    @NotNull(message = "Base ID不能为空")
    private Long baseId;

    private Long tableId; // 可选，限定查询范围

    @NotBlank(message = "问题不能为空")
    private String question;

    public Long getBaseId() {
        return baseId;
    }

    public void setBaseId(Long baseId) {
        this.baseId = baseId;
    }

    public Long getTableId() {
        return tableId;
    }

    public void setTableId(Long tableId) {
        this.tableId = tableId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}