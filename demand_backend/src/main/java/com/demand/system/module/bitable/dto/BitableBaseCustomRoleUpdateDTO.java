package com.demand.system.module.bitable.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class BitableBaseCustomRoleUpdateDTO {

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 100, message = "角色名称最多100字符")
    private String name;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
