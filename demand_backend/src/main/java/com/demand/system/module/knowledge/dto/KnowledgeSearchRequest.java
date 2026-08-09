package com.demand.system.module.knowledge.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public class KnowledgeSearchRequest {

    @NotBlank(message = "检索内容不能为空")
    private String query;

    private Long knowledgeBaseId;

    private String mode = "hybrid";

    private Integer topK = 20;

    private Long llmModelId;

    /** 显式检索范围：REQUIREMENT_BODY、KNOWLEDGE_BASE、WEB。 */
    private List<String> searchScopes;

    /** 后端内部传递的检索用户，禁止客户端注入。 */
    @JsonIgnore
    private Long requesterId;

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

    public List<String> getSearchScopes() {
        return searchScopes;
    }

    public void setSearchScopes(List<String> searchScopes) {
        this.searchScopes = searchScopes;
    }

    public Long getLlmModelId() {
        return llmModelId;
    }

    public void setLlmModelId(Long llmModelId) {
        this.llmModelId = llmModelId;
    }

    public Long getRequesterId() {
        return requesterId;
    }

    public void setRequesterId(Long requesterId) {
        this.requesterId = requesterId;
    }
}
