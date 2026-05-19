package com.demand.system.module.auth.dto;

import java.util.List;

/**
 * 用户信息响应DTO
 */
public class UserInfoResponse {

    private Long id;
    private String username;
    private String realName;
    private String email;
    private String avatar;
    private List<String> roles;
    private List<String> permissions;
    private Boolean isSuperAdmin;
    private Long regionId;
    private Long departmentId;
    private Long positionId;

    public UserInfoResponse() {
    }

    public UserInfoResponse(Long id, String username, String realName, String email, String avatar,
                           List<String> roles, List<String> permissions, Boolean isSuperAdmin,
                           Long regionId, Long departmentId, Long positionId) {
        this.id = id;
        this.username = username;
        this.realName = realName;
        this.email = email;
        this.avatar = avatar;
        this.roles = roles;
        this.permissions = permissions;
        this.isSuperAdmin = isSuperAdmin;
        this.regionId = regionId;
        this.departmentId = departmentId;
        this.positionId = positionId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }

    public Boolean getIsSuperAdmin() {
        return isSuperAdmin;
    }

    public void setIsSuperAdmin(Boolean isSuperAdmin) {
        this.isSuperAdmin = isSuperAdmin;
    }

    public Long getRegionId() {
        return regionId;
    }

    public void setRegionId(Long regionId) {
        this.regionId = regionId;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public Long getPositionId() {
        return positionId;
    }

    public void setPositionId(Long positionId) {
        this.positionId = positionId;
    }

    public static UserInfoResponseBuilder builder() {
        return new UserInfoResponseBuilder();
    }

    public static class UserInfoResponseBuilder {
        private Long id;
        private String username;
        private String realName;
        private String email;
        private String avatar;
        private List<String> roles;
        private List<String> permissions;
        private Boolean isSuperAdmin;
        private Long regionId;
        private Long departmentId;
        private Long positionId;

        public UserInfoResponseBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public UserInfoResponseBuilder username(String username) {
            this.username = username;
            return this;
        }

        public UserInfoResponseBuilder realName(String realName) {
            this.realName = realName;
            return this;
        }

        public UserInfoResponseBuilder email(String email) {
            this.email = email;
            return this;
        }

        public UserInfoResponseBuilder avatar(String avatar) {
            this.avatar = avatar;
            return this;
        }

        public UserInfoResponseBuilder roles(List<String> roles) {
            this.roles = roles;
            return this;
        }

        public UserInfoResponseBuilder permissions(List<String> permissions) {
            this.permissions = permissions;
            return this;
        }

        public UserInfoResponseBuilder isSuperAdmin(Boolean isSuperAdmin) {
            this.isSuperAdmin = isSuperAdmin;
            return this;
        }

        public UserInfoResponseBuilder regionId(Long regionId) {
            this.regionId = regionId;
            return this;
        }

        public UserInfoResponseBuilder departmentId(Long departmentId) {
            this.departmentId = departmentId;
            return this;
        }

        public UserInfoResponseBuilder positionId(Long positionId) {
            this.positionId = positionId;
            return this;
        }

        public UserInfoResponse build() {
            return new UserInfoResponse(id, username, realName, email, avatar, roles,
                    permissions, isSuperAdmin, regionId, departmentId, positionId);
        }
    }
}
