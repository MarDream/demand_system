package com.demand.system.module.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KnowledgeSearchRequest {

    @NotBlank(message = "检索内容不能为空")
    private String query;

    private Long knowledgeBaseId;

    private String mode = "hybrid";

    private Integer topK = 20;

    private Long llmModelId;
}
