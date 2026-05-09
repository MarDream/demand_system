package com.demand.system.module.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
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

    private String vectorId;

    private LocalDateTime createdAt;
}
