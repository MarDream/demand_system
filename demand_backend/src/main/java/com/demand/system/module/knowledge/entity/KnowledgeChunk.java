package com.demand.system.module.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;
import java.util.Objects;

@TableName("knowledge_chunks")
public class KnowledgeChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long documentId;

    private Long knowledgeBaseId;

    private Integer chunkIndex;

    private String content;

    private String sectionTitle;

    private Integer pageNum;

    private Integer charCount;

    /** 来源内容类型：body、image_ocr、image_caption。 */
    private String sourceContentType;

    /** 来源引用ID，例如正文图片文件ID。 */
    private Long sourceRefId;

    /** 来源位置，例如正文图片序号。 */
    private Integer sourcePosition;

    private String vectorId;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSectionTitle() {
        return sectionTitle;
    }

    public void setSectionTitle(String sectionTitle) {
        this.sectionTitle = sectionTitle;
    }

    public Integer getPageNum() {
        return pageNum;
    }

    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
    }

    public Integer getCharCount() {
        return charCount;
    }

    public void setCharCount(Integer charCount) {
        this.charCount = charCount;
    }

    public String getSourceContentType() {
        return sourceContentType;
    }

    public void setSourceContentType(String sourceContentType) {
        this.sourceContentType = sourceContentType;
    }

    public Long getSourceRefId() {
        return sourceRefId;
    }

    public void setSourceRefId(Long sourceRefId) {
        this.sourceRefId = sourceRefId;
    }

    public Integer getSourcePosition() {
        return sourcePosition;
    }

    public void setSourcePosition(Integer sourcePosition) {
        this.sourcePosition = sourcePosition;
    }

    public String getVectorId() {
        return vectorId;
    }

    public void setVectorId(String vectorId) {
        this.vectorId = vectorId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KnowledgeChunk that = (KnowledgeChunk) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
