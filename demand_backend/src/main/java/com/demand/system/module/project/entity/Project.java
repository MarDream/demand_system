package com.demand.system.module.project.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName(value = "projects", autoResultMap = true)
public class Project {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private Long companyId;

    private String team;

    private Long leaderId;

    private LocalDate startDate;

    private LocalDate endDate;

    private Long creatorId;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deletedAt;
}
