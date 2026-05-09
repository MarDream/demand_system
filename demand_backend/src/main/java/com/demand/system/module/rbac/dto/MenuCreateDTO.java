package com.demand.system.module.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MenuCreateDTO {

    private Long parentId;

    @NotBlank(message = "菜单名称不能为空")
    private String name;

    @NotBlank(message = "菜单类型不能为空")
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
}
