package com.demand.system.module.rbac.dto;

import java.util.List;

public class RolePermissionVO {

    private Long roleId;
    private String roleCode;
    private String roleName;
    private List<String> permissionCodes;
    private List<String> grantablePermissionCodes;
    private List<Long> dataScopeOrgIds;

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public List<String> getPermissionCodes() {
        return permissionCodes;
    }

    public void setPermissionCodes(List<String> permissionCodes) {
        this.permissionCodes = permissionCodes;
    }

    public List<String> getGrantablePermissionCodes() {
        return grantablePermissionCodes;
    }

    public void setGrantablePermissionCodes(List<String> grantablePermissionCodes) {
        this.grantablePermissionCodes = grantablePermissionCodes;
    }

    public List<Long> getDataScopeOrgIds() {
        return dataScopeOrgIds;
    }

    public void setDataScopeOrgIds(List<Long> dataScopeOrgIds) {
        this.dataScopeOrgIds = dataScopeOrgIds;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long roleId;
        private String roleCode;
        private String roleName;
        private List<String> permissionCodes;
        private List<String> grantablePermissionCodes;
        private List<Long> dataScopeOrgIds;

        public Builder roleId(Long roleId) {
            this.roleId = roleId;
            return this;
        }

        public Builder roleCode(String roleCode) {
            this.roleCode = roleCode;
            return this;
        }

        public Builder roleName(String roleName) {
            this.roleName = roleName;
            return this;
        }

        public Builder permissionCodes(List<String> permissionCodes) {
            this.permissionCodes = permissionCodes;
            return this;
        }

        public Builder grantablePermissionCodes(List<String> grantablePermissionCodes) {
            this.grantablePermissionCodes = grantablePermissionCodes;
            return this;
        }

        public Builder dataScopeOrgIds(List<Long> dataScopeOrgIds) {
            this.dataScopeOrgIds = dataScopeOrgIds;
            return this;
        }

        public RolePermissionVO build() {
            RolePermissionVO vo = new RolePermissionVO();
            vo.setRoleId(roleId);
            vo.setRoleCode(roleCode);
            vo.setRoleName(roleName);
            vo.setPermissionCodes(permissionCodes);
            vo.setGrantablePermissionCodes(grantablePermissionCodes);
            vo.setDataScopeOrgIds(dataScopeOrgIds);
            return vo;
        }
    }
}
