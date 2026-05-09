package com.demand.system.module.rbac.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_menus")
public class SysMenu {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parentId;

    private String name;

    private String menuType;

    private String path;

    private String routeName;

    private String component;

    private String icon;

    private Integer sortOrder;

    private String permissionCode;

    private Integer visible;

    private Integer enabled;

    private Integer keepAlive;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
