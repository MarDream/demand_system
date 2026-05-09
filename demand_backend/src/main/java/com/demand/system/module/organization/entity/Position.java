package com.demand.system.module.organization.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "positions", autoResultMap = true)
public class Position {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String code;

    private String level;

    private String description;

    private Long regionId;

    private Long departmentId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> menuPermissions;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deletedAt;
}
