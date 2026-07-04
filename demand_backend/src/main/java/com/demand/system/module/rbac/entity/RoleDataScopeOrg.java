package com.demand.system.module.rbac.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 角色数据权限-可见组织范围实体
 * 用于角色管理中进行组织架构级的数据权限控制，
 * 限定角色下用户可见的需求创建人所属组织范围。
 */
@TableName("role_data_scope_orgs")
public class RoleDataScopeOrg {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roleId;

    private Long orgId;

    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public RoleDataScopeOrg() {}
    public RoleDataScopeOrg(Long roleId, Long orgId) {
        this.roleId = roleId;
        this.orgId = orgId;
    }
}
