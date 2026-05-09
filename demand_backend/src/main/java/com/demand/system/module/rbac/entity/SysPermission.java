package com.demand.system.module.rbac.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_permissions")
public class SysPermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    private String type;

    private String description;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
