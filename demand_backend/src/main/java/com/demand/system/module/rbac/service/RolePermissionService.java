package com.demand.system.module.rbac.service;

import com.demand.system.common.result.Result;
import com.demand.system.module.rbac.dto.RoleCreateDTO;
import com.demand.system.module.rbac.dto.RolePermissionSaveDTO;
import com.demand.system.module.rbac.dto.RolePermissionVO;
import com.demand.system.module.rbac.dto.RoleUpdateDTO;
import com.demand.system.module.rbac.dto.RoleVO;

import java.util.List;

public interface RolePermissionService {

    Result<List<RoleVO>> listRoles();

    Result<RoleVO> createRole(RoleCreateDTO request);

    Result<RoleVO> updateRole(RoleUpdateDTO request);

    Result<Void> deleteRole(Long roleId);

    Result<RolePermissionVO> getRolePermissions(Long roleId);

    Result<Void> saveRolePermissions(RolePermissionSaveDTO request);

    Result<List<String>> getCurrentGrantablePermissionCodes();
}
