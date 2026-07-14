package com.demand.system.module.bitable.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * AI 自然语言建表请求 DTO
 */
public class AiBuildTableRequest {

    @NotBlank(message = "描述不能为空")
    private String description;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}