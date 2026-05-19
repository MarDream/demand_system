package com.demand.system.module.rbac.dto;

import jakarta.validation.constraints.NotBlank;

public class DocumentSearchRequest {

    @NotBlank(message = "检索内容不能为空")
    private String query;

    private String mode = "hybrid";

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }
}
