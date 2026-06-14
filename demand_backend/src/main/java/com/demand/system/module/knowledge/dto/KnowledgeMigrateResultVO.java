package com.demand.system.module.knowledge.dto;

/**
 * 知识库文档迁移结果。
 */
public class KnowledgeMigrateResultVO {

    private int migratedDocuments;
    private int migratedChunks;
    private Long sourceKnowledgeBaseId;
    private Long targetKnowledgeBaseId;

    public KnowledgeMigrateResultVO() {
    }

    public KnowledgeMigrateResultVO(int migratedDocuments, int migratedChunks,
                                    Long sourceKnowledgeBaseId, Long targetKnowledgeBaseId) {
        this.migratedDocuments = migratedDocuments;
        this.migratedChunks = migratedChunks;
        this.sourceKnowledgeBaseId = sourceKnowledgeBaseId;
        this.targetKnowledgeBaseId = targetKnowledgeBaseId;
    }

    public int getMigratedDocuments() {
        return migratedDocuments;
    }

    public void setMigratedDocuments(int migratedDocuments) {
        this.migratedDocuments = migratedDocuments;
    }

    public int getMigratedChunks() {
        return migratedChunks;
    }

    public void setMigratedChunks(int migratedChunks) {
        this.migratedChunks = migratedChunks;
    }

    public Long getSourceKnowledgeBaseId() {
        return sourceKnowledgeBaseId;
    }

    public void setSourceKnowledgeBaseId(Long sourceKnowledgeBaseId) {
        this.sourceKnowledgeBaseId = sourceKnowledgeBaseId;
    }

    public Long getTargetKnowledgeBaseId() {
        return targetKnowledgeBaseId;
    }

    public void setTargetKnowledgeBaseId(Long targetKnowledgeBaseId) {
        this.targetKnowledgeBaseId = targetKnowledgeBaseId;
    }
}
