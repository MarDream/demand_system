package com.demand.system.module.knowledge.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 知识库文档迁移请求。
 *
 * 将指定知识库下的所有文档（也可限定子集）迁移至目标知识库：
 * 1. 更新 knowledge_documents.knowledge_base_id
 * 2. 更新 knowledge_chunks.knowledge_base_id
 * 3. 删除原 Milvus 向量（异步重新索引）
 * 4. 调整两端的 docCount / chunkCount
 * 5. 触发 RabbitMQ 重新消费以重建向量
 */
public class KnowledgeBaseMigrateDTO {

    @NotNull(message = "目标知识库不能为空")
    private Long targetKnowledgeBaseId;

    /**
     * 可选：仅迁移指定文档 ID。空集合表示迁移全部文档。
     */
    private List<Long> documentIds;

    /**
     * 备注/原因（用于审计日志，可选）
     */
    @Size(max = 200)
    private String reason;

    public Long getTargetKnowledgeBaseId() {
        return targetKnowledgeBaseId;
    }

    public void setTargetKnowledgeBaseId(Long targetKnowledgeBaseId) {
        this.targetKnowledgeBaseId = targetKnowledgeBaseId;
    }

    public List<Long> getDocumentIds() {
        return documentIds;
    }

    public void setDocumentIds(List<Long> documentIds) {
        this.documentIds = documentIds;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
