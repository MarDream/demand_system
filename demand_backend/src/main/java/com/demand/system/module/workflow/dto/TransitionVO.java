package com.demand.system.module.workflow.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TransitionVO {

    private Long id;

    private Long instanceId;

    private Long requirementId;

    private String fromNodeId;

    private String fromNodeName;

    private String toNodeId;

    private String toNodeName;

    private Long operatorId;

    private String operatorName;

    private String action;

    private String comment;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private Long durationSeconds;

    private String durationDisplay;

    private LocalDateTime createdAt;
}
