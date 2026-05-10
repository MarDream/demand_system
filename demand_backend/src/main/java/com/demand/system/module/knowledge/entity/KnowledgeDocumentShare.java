package com.demand.system.module.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_document_shares")
public class KnowledgeDocumentShare {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String token;

    private Long knowledgeBaseId;

    private Long documentId;

    private Long creatorId;

    private Integer requireLogin;

    private Integer oneTimeAccess;

    private Integer usedCount;

    private String status;

    private LocalDateTime expireAt;

    private LocalDateTime usedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
