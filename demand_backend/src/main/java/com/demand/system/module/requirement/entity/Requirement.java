package com.demand.system.module.requirement.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.demand.system.module.requirement.dto.RequirementAttachmentDTO;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "requirements", autoResultMap = true)
public class Requirement {

    @TableId(type = IdType.AUTO)
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

    private Long workflowInstanceId;

    private String nodeStatus;

    private Boolean isDraft;

    private LocalDate startDate;

    private BigDecimal estimatedHours;

    private BigDecimal actualHours;

    private LocalDate dueDate;

    private LocalDateTime analysisCompletedAt;

    private LocalDateTime confirmAt;

    private LocalDateTime developmentCompletedAt;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<RequirementAttachmentDTO> attachments;

    private Integer orderNum;

    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deletedAt;
}
