package com.demand.system.module.knowledge.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class KnowledgeBaseVO {

    private Long id;
    private String name;
    private String description;
    private Long projectId;
    private String projectName;
    private Long creatorId;
    private String creatorName;
    private Integer docCount;
    private Integer chunkCount;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
