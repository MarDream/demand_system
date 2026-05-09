package com.demand.system.module.rbac.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class MenuVO {

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

    private Boolean granted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private List<MenuVO> children = new ArrayList<>();
}
