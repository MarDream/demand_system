package com.demand.system.module.rbac.controller;

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
import com.demand.system.module.rbac.service.RolePermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "角色授权管理", description = "RBAC角色授权接口")
@RestController
@RequestMapping("/api/v1/rbac/roles")
@PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN')")
public class RolePermissionController {

    private final RolePermissionService rolePermissionService;

    public RolePermissionController(RolePermissionService rolePermissionService) {
        this.rolePermissionService = rolePermissionService;
    }

    @Operation(summary = "查询角色列表")
    @GetMapping
    public Result<List<RoleVO>> listRoles() {
        return rolePermissionService.listRoles();
    }

    @Operation(summary = "查询角色组列表")
    @GetMapping("/groups")
    public Result<List<RoleGroupVO>> listRoleGroups() {
        return rolePermissionService.listRoleGroups();
    }

    @Operation(summary = "创建角色组")
    @PostMapping("/groups")
    public Result<RoleGroupVO> createRoleGroup(@Valid @RequestBody RoleGroupCreateDTO request) {
        return rolePermissionService.createRoleGroup(request);
    }

    @Operation(summary = "更新角色组")
    @PutMapping("/groups/{groupId}")
    public Result<RoleGroupVO> updateRoleGroup(@PathVariable Long groupId, @Valid @RequestBody RoleGroupUpdateDTO request) {
        request.setId(groupId);
        return rolePermissionService.updateRoleGroup(request);
    }

    @Operation(summary = "删除角色组")
    @DeleteMapping("/groups/{groupId}")
    public Result<Void> deleteRoleGroup(@PathVariable Long groupId) {
        return rolePermissionService.deleteRoleGroup(groupId);
    }

    @Operation(summary = "创建角色")
    @PostMapping
    public Result<RoleVO> createRole(@Valid @RequestBody RoleCreateDTO request) {
        return rolePermissionService.createRole(request);
    }

    @Operation(summary = "更新角色")
    @PutMapping("/{roleId}")
    public Result<RoleVO> updateRole(@PathVariable Long roleId, @Valid @RequestBody RoleUpdateDTO request) {
        request.setId(roleId);
        return rolePermissionService.updateRole(request);
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/{roleId}")
    public Result<Void> deleteRole(@PathVariable Long roleId) {
        return rolePermissionService.deleteRole(roleId);
    }

    @Operation(summary = "查询角色权限")
    @GetMapping("/{roleId}/permissions")
    public Result<RolePermissionVO> getRolePermissions(@PathVariable Long roleId) {
        return rolePermissionService.getRolePermissions(roleId);
    }

    @Operation(summary = "保存角色权限")
    @PutMapping("/{roleId}/permissions")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:role:grant')")
    public Result<Void> saveRolePermissions(@PathVariable Long roleId,
                                            @Valid @RequestBody RolePermissionSaveDTO request) {
        request.setRoleId(roleId);
        return rolePermissionService.saveRolePermissions(request);
    }

    @Operation(summary = "查询当前用户可授权权限编码")
    @GetMapping("/grantable-permissions")
    public Result<List<String>> getCurrentGrantablePermissionCodes() {
        return rolePermissionService.getCurrentGrantablePermissionCodes();
    }

    @Operation(summary = "批量排序角色组")
    @PutMapping("/groups/batch-sort")
    public Result<Void> batchSortRoleGroups(@RequestBody List<RoleGroupSortItem> items) {
        return rolePermissionService.batchSortRoleGroups(items);
    }

    @Operation(summary = "批量排序角色")
    @PutMapping("/batch-sort")
    public Result<Void> batchSortRoles(@RequestBody List<RoleSortItem> items) {
        return rolePermissionService.batchSortRoles(items);
    }

}
