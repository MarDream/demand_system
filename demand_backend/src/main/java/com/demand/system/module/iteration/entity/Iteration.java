package com.demand.system.module.iteration.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("iterations")
public class Iteration {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String name;

    private String description;

    private LocalDate startDate;

    private LocalDate endDate;

    private BigDecimal capacity;

    private String status;

    private Long creatorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deletedAt;
}
