package com.demand.system.module.rbac.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class RolePermissionSaveDTO {

    @NotNull(message = "角色ID不能为空")
    private Long roleId;

    private List<String> permissionCodes;
}
