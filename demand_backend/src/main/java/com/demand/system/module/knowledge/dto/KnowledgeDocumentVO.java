package com.demand.system.module.knowledge.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class KnowledgeDocumentVO {

    private Long id;
    private Long knowledgeBaseId;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private Integer chunkCount;
    private String status;
    private String errorMessage;
    private Long uploaderId;
    private String uploaderName;
    private LocalDateTime createdAt;
}
