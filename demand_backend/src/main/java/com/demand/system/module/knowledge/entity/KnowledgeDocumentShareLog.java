package com.demand.system.module.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_document_share_logs")
public class KnowledgeDocumentShareLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long shareId;

    private Long documentId;

    private Long accessUserId;

    private String accessIp;

    private String userAgent;

    private String accessStatus;

    private String failureReason;

    private LocalDateTime createdAt;
}
