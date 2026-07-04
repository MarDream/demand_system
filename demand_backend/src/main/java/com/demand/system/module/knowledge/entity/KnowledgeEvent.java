package com.demand.system.module.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 知识库事件实体类
 * 对应表: knowledge_events
 */
@TableName("knowledge_events")
public class KnowledgeEvent {

    /**
     * 事件ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 知识库ID
     */
    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;

    /**
     * 文档ID
     */
    @TableField("document_id")
    private Long documentId;

    /**
     * 分段ID
     */
    @TableField("chunk_id")
    private Long chunkId;

    /**
     * 事件标题
     */
    private String title;

    /**
     * 事件摘要
     */
    private String summary;

    /**
     * 事件内容
     */
    private String content;

    /**
     * 分类
     */
    private String category;

    /**
     * 关键词(JSON格式)
     */
    @TableField("keywords")
    private String keywords;

    /**
     * 优先级: high/medium/low
     */
    private String priority;

    /**
     * 状态: open/closed/resolved
     */
    private String status;

    /**
     * 标题向量
     */
    @TableField("title_embedding")
    private String titleEmbedding;

    /**
     * 内容向量
     */
    @TableField("content_embedding")
    private String contentEmbedding;

    /**
     * 分段排名
     */
    @TableField("chunk_rank")
    private Integer chunkRank;

    /**
     * 删除时间(软删除)
     */
    @TableLogic(value = "deleted_at", delval = "CURRENT_TIMESTAMP")
    private LocalDateTime deletedAt;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Long getChunkId() {
        return chunkId;
    }

    public void setChunkId(Long chunkId) {
        this.chunkId = chunkId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTitleEmbedding() {
        return titleEmbedding;
    }

    public void setTitleEmbedding(String titleEmbedding) {
        this.titleEmbedding = titleEmbedding;
    }

    public String getContentEmbedding() {
        return contentEmbedding;
    }

    public void setContentEmbedding(String contentEmbedding) {
        this.contentEmbedding = contentEmbedding;
    }

    public Integer getChunkRank() {
        return chunkRank;
    }

    public void setChunkRank(Integer chunkRank) {
        this.chunkRank = chunkRank;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KnowledgeEvent that = (KnowledgeEvent) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
