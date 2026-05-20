package com.demand.system.module.rbac.service;

import com.demand.system.common.result.Result;
import com.demand.system.module.rbac.dto.RoleCreateDTO;
import com.demand.system.module.rbac.dto.RoleGroupCreateDTO;
import com.demand.system.module.rbac.dto.RoleGroupSortItem;
import com.demand.system.module.rbac.dto.RoleGroupUpdateDTO;
import com.demand.system.module.rbac.dto.RoleGroupVO;
import com.demand.system.module.rbac.dto.RolePermissionSaveDTO;
import com.demand.system.module.rbac.dto.RolePermissionVO;
import com.demand.system.module.rbac.dto.RoleSortItem;
import com.demand.system.module.rbac.dto.RoleUpdateDTO;
import com.demand.system.module.rbac.dto.RoleVO;

import java.util.List;

public interface RolePermissionService {

    Result<List<RoleVO>> listRoles();

    Result<List<RoleGroupVO>> listRoleGroups();

    Result<RoleGroupVO> createRoleGroup(RoleGroupCreateDTO request);

    Result<RoleGroupVO> updateRoleGroup(RoleGroupUpdateDTO request);

    Result<Void> deleteRoleGroup(Long roleGroupId);

    Result<RoleVO> createRole(RoleCreateDTO request);

    Result<RoleVO> updateRole(RoleUpdateDTO request);

    Result<Void> deleteRole(Long roleId);

    Result<RolePermissionVO> getRolePermissions(Long roleId);

    Result<Void> saveRolePermissions(RolePermissionSaveDTO request);

    Result<List<String>> getCurrentGrantablePermissionCodes();

    Result<Void> batchSortRoleGroups(List<RoleGroupSortItem> items);

    Result<Void> batchSortRoles(List<RoleSortItem> items);
}
