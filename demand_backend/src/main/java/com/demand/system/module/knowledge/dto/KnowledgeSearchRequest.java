package com.demand.system.module.knowledge.dto;

import jakarta.validation.constraints.NotBlank;

public class KnowledgeSearchRequest {

    @NotBlank(message = "检索内容不能为空")
    private String query;

    private Long knowledgeBaseId;

    private String mode = "hybrid";

    private Integer topK = 20;

    private Long llmModelId;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public Long getLlmModelId() {
        return llmModelId;
    }

    public void setLlmModelId(Long llmModelId) {
        this.llmModelId = llmModelId;
    }
}
