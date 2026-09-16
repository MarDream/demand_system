package com.demand.system.module.bitable.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class BitableBaseRolePermissionDTO {

    @NotNull(message = "Base ID不能为空")
    private Long baseId;

    @NotBlank(message = "角色类型不能为空")
    private String roleType;

    private String systemRoleCode;

    private Long customRoleId;

    @NotNull(message = "数据表ID不能为空")
    private Long tableId;

    @NotBlank(message = "权限类型不能为空")
    private String permissionType;

    @NotBlank(message = "权限级别不能为空")
    private String permissionLevel;

    public Long getBaseId() { return baseId; }
    public void setBaseId(Long baseId) { this.baseId = baseId; }

    public String getRoleType() { return roleType; }
    public void setRoleType(String roleType) { this.roleType = roleType; }

    public String getSystemRoleCode() { return systemRoleCode; }
    public void setSystemRoleCode(String systemRoleCode) { this.systemRoleCode = systemRoleCode; }

    public Long getCustomRoleId() { return customRoleId; }
    public void setCustomRoleId(Long customRoleId) { this.customRoleId = customRoleId; }

    public Long getTableId() { return tableId; }
    public void setTableId(Long tableId) { this.tableId = tableId; }

    public String getPermissionType() { return permissionType; }
    public void setPermissionType(String permissionType) { this.permissionType = permissionType; }

    public String getPermissionLevel() { return permissionLevel; }
    public void setPermissionLevel(String permissionLevel) { this.permissionLevel = permissionLevel; }
}
