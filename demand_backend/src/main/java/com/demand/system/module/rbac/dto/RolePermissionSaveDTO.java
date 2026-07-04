package com.demand.system.module.rbac.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public class RolePermissionSaveDTO {

    @NotNull(message = "角色ID不能为空")
    private Long roleId;

    private List<String> permissionCodes;

    private List<Long> dataScopeOrgIds;

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public List<String> getPermissionCodes() {
        return permissionCodes;
    }

    public void setPermissionCodes(List<String> permissionCodes) {
        this.permissionCodes = permissionCodes;
    }

    public List<Long> getDataScopeOrgIds() {
        return dataScopeOrgIds;
    }

    public void setDataScopeOrgIds(List<Long> dataScopeOrgIds) {
        this.dataScopeOrgIds = dataScopeOrgIds;
    }
}
