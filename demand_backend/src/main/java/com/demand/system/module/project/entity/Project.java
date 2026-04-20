package com.demand.system.module.project.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("projects")
public class Project {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private Long creatorId;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deletedAt;
}
