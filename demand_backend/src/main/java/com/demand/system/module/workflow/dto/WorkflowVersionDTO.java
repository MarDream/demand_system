package com.demand.system.module.workflow.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkflowVersionDTO {

    private Long id;

    private Long projectId;

    private Integer version;

    private String name;

    private Integer isActive;

    private Long creatorId;

    private String creatorName;

    private LocalDateTime createdAt;

    private WorkflowConfigDTO config;
}
