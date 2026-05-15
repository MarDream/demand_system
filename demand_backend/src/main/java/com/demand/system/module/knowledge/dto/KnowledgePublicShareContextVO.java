package com.demand.system.module.knowledge.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class KnowledgePublicShareContextVO {

    private String shareToken;

    private String accessToken;

    private Long knowledgeBaseId;

    private Long documentId;

    private String fileName;

    private String fileType;

    private LocalDateTime expireAt;

    private Boolean requireLogin;

    private Boolean oneTimeAccess;
}
