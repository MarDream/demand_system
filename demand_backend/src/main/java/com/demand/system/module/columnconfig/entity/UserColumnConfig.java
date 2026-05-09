package com.demand.system.module.columnconfig.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_column_configs")
public class UserColumnConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String pageKey;

    private String visibleColumns;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}