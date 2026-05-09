package com.demand.system.module.workflow.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkflowApprovalDTO {

    private Long id;

    private Long workflowVersionId;

    private Long submitterId;

    private String submitterName;

    private Long projectId;

    private String projectName;

    private Integer version;

    private String status;

    private String comment;

    private LocalDateTime submittedAt;

    private LocalDateTime approvedAt;
}
