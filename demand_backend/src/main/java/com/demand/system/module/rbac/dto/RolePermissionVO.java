package com.demand.system.module.rbac.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RolePermissionVO {

    private Long roleId;

    private String roleCode;

    private String roleName;

    private List<String> permissionCodes;

    private List<String> grantablePermissionCodes;
}
