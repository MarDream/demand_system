package com.demand.system.module.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_bases")
public class KnowledgeBase {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private Long projectId;

    private Long creatorId;

    private Integer docCount;

    private Integer chunkCount;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deletedAt;
}
