package com.demand.system.module.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public class RoleGroupCreateDTO {

    @NotBlank(message = "角色组名称不能为空")
    @Size(max = 100, message = "角色组名称不能超过100个字符")
    private String name;

    @Size(max = 500, message = "角色组描述不能超过500个字符")
    private String description;

    private List<Long> roleIds;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<Long> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(List<Long> roleIds) {
        this.roleIds = roleIds;
    }
}
