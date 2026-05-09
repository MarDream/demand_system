package com.demand.system.module.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DocumentSearchRequest {

    @NotBlank(message = "检索内容不能为空")
    private String query;

    private String mode = "hybrid";
}
