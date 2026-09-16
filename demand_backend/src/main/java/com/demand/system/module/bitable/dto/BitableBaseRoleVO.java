package com.demand.system.module.bitable.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 多维表格角色VO（系统角色 + 自定义角色统一视图）
 */
public class BitableBaseRoleVO {

    private String roleType;
    private String systemRoleCode;
    private Long customRoleId;
    private String name;
    private Integer sortOrder;
    private List<MemberVO> members;
    private List<PermissionVO> permissions;
    private LocalDateTime createdAt;

    public String getRoleType() { return roleType; }
    public void setRoleType(String roleType) { this.roleType = roleType; }

    public String getSystemRoleCode() { return systemRoleCode; }
    public void setSystemRoleCode(String systemRoleCode) { this.systemRoleCode = systemRoleCode; }

    public Long getCustomRoleId() { return customRoleId; }
    public void setCustomRoleId(Long customRoleId) { this.customRoleId = customRoleId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public List<MemberVO> getMembers() { return members; }
    public void setMembers(List<MemberVO> members) { this.members = members; }

    public List<PermissionVO> getPermissions() { return permissions; }
    public void setPermissions(List<PermissionVO> permissions) { this.permissions = permissions; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public static class MemberVO {
        private String memberType;
        private Long memberId;
        private String memberName;
        /** 成员头像URL，用户类成员才有；为空时前端用姓名首字做占位缩略图 */
        private String memberAvatar;

        public String getMemberType() { return memberType; }
        public void setMemberType(String memberType) { this.memberType = memberType; }

        public Long getMemberId() { return memberId; }
        public void setMemberId(Long memberId) { this.memberId = memberId; }

        public String getMemberName() { return memberName; }
        public void setMemberName(String memberName) { this.memberName = memberName; }

        public String getMemberAvatar() { return memberAvatar; }
        public void setMemberAvatar(String memberAvatar) { this.memberAvatar = memberAvatar; }
    }

    public static class PermissionVO {
        private Long tableId;
        private String tableName;
        private String permissionType;
        private String permissionLevel;

        public Long getTableId() { return tableId; }
        public void setTableId(Long tableId) { this.tableId = tableId; }

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }

        public String getPermissionType() { return permissionType; }
        public void setPermissionType(String permissionType) { this.permissionType = permissionType; }

        public String getPermissionLevel() { return permissionLevel; }
        public void setPermissionLevel(String permissionLevel) { this.permissionLevel = permissionLevel; }
    }
}
