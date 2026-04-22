package com.demand.system.module.requirement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "requirement_types", autoResultMap = true)
public class RequirementTypeConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    private String color;

    private Integer sortOrder;

    private Boolean isDefault;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
