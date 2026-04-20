package com.demand.system.module.project.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_members")
public class ProjectMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private Long userId;

    private String role;

    private LocalDateTime joinedAt;

    // 以下为关联查询字段，非数据库列
    @TableField(exist = false)
    private String username;

    @TableField(exist = false)
    private String realName;
}
