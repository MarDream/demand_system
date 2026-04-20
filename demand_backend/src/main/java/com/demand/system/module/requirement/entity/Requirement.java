package com.demand.system.module.requirement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName(value = "requirements", autoResultMap = true)
public class Requirement {

    @TableId(type = IdType.AUTO)
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

    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deletedAt;
}
