package com.demand.system.module.requirement.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class RequirementVO {

    private Long id;

    private Long projectId;

    private Long parentId;

    private Long creatorId;

    private Long assigneeId;

    private Long opsFollowId;

    private Long maintFollowId;

    private Long departmentId;

    private String title;

    private String description;

    private String type;

    private String priority;

    private String status;

    private Long moduleId;

    private Long iterationId;

    private LocalDate startDate;

    private BigDecimal estimatedHours;

    private BigDecimal actualHours;

    private LocalDate dueDate;

    private LocalDateTime analysisCompletedAt;

    private LocalDateTime confirmAt;

    private LocalDateTime developmentCompletedAt;

    private List<RequirementAttachmentDTO> attachments;

    private Integer orderNum;

    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer deletedAt;

    private String creatorName;

    private String assigneeName;

    private String opsFollowName;

    private String maintFollowName;

    private String departmentName;

    private Integer childCount;
}
