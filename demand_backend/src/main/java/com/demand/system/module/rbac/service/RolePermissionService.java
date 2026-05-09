package com.demand.system.module.rbac.service;

import com.demand.system.common.result.Result;
import com.demand.system.module.rbac.dto.RolePermissionSaveDTO;
import com.demand.system.module.rbac.dto.RolePermissionVO;

import java.util.List;

public interface RolePermissionService {

    Result<RolePermissionVO> getRolePermissions(Long roleId);

    Result<Void> saveRolePermissions(RolePermissionSaveDTO request);

    Result<List<String>> getCurrentGrantablePermissionCodes();
}
