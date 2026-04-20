package com.demand.system.module.requirement.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class RequirementVO {

    private Long id;

    private Long projectId;

    private Long parentId;

    private Long creatorId;

    private Long assigneeId;

    private String title;

    private String description;

    private String type;

    private String priority;

    private String status;

    private Long moduleId;

    private Long iterationId;

    private BigDecimal estimatedHours;

    private BigDecimal actualHours;

    private LocalDate dueDate;

    private Integer orderNum;

    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer deletedAt;

    private String creatorName;

    private String assigneeName;

    private Integer childCount;
}
