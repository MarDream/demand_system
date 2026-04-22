package com.demand.system.module.requirement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "priorities", autoResultMap = true)
public class PriorityConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    private String color;

    private Integer level;

    private Integer sortOrder;

    private Boolean isDefault;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
