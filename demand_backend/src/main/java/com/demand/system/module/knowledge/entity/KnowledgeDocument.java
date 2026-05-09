package com.demand.system.module.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_documents")
public class KnowledgeDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long knowledgeBaseId;

    private String fileName;

    private String fileType;

    private Long fileSize;

    private Integer chunkCount;

    private String status;

    private String errorMessage;

    private String minioKey;

    private Long uploaderId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deletedAt;
}
