package com.demand.system.module.knowledge.dto;

import jakarta.validation.constraints.NotNull;

/** 预览弹窗按当前问题补查某个文档的具体命中段落。 */
public class CitationSnippetRequest {

    private String query;

    @NotNull(message = "知识库不能为空")
    private Long knowledgeBaseId;

    @NotNull(message = "文档不能为空")
    private Long documentId;

    private Integer topK = 3;

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

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }
}
