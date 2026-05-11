package com.demand.system.module.organization.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_org")
public class SysOrg {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Long parentId;

    private String orgType;

    private String code;

    private Long leaderId;

    private String description;

    private Integer sortOrder;

    private String path;

    private Integer level;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deletedAt;
}
