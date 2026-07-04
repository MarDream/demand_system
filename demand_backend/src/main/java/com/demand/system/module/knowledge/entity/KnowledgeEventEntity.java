package com.demand.system.module.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import java.util.Objects;

/**
 * 知识库事件实体关联实体类
 * 对应表: knowledge_event_entities
 * 联合主键 (event_id, entity_id)，MyBatis-Plus 无单字段主键。
 * 若需直接插入/删除，请在 Mapper XML 中写手动 SQL，或使用 eventId/entityId 条件操作。
 */
@TableName("knowledge_event_entities")
public class KnowledgeEventEntity {

    /**
     * 事件ID(联合主键)
     */
    @TableField("event_id")
    private Long eventId;

    /**
     * 实体ID(联合主键)
     */
    @TableField("entity_id")
    private Long entityId;

    /**
     * 关联向量(JSON格式)
     */
    @TableField("embedding")
    private String embedding;

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public String getEmbedding() {
        return embedding;
    }

    public void setEmbedding(String embedding) {
        this.embedding = embedding;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KnowledgeEventEntity that = (KnowledgeEventEntity) o;
        return Objects.equals(eventId, that.eventId) &&
               Objects.equals(entityId, that.entityId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, entityId);
    }
}
