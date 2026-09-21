package com.demand.system.module.bitable.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class BitableBaseCustomRoleCreateDTO {

    @NotNull(message = "Base ID不能为空")
    private Long baseId;

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 100, message = "角色名称最多100字符")
    private String name;

    /** 创建成功后由后端回填新生成的角色ID */
    private Long customRoleId;

    public Long getBaseId() { return baseId; }
    public void setBaseId(Long baseId) { this.baseId = baseId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Long getCustomRoleId() { return customRoleId; }
    public void setCustomRoleId(Long customRoleId) { this.customRoleId = customRoleId; }
}
