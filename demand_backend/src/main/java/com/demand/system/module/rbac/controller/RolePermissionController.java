package com.demand.system.module.rbac.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.rbac.dto.RolePermissionSaveDTO;
import com.demand.system.module.rbac.dto.RolePermissionVO;
import com.demand.system.module.rbac.dto.RoleVO;
import com.demand.system.module.rbac.entity.Role;
import com.demand.system.module.rbac.mapper.RoleMapper;
import com.demand.system.module.rbac.service.RolePermissionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "角色授权管理", description = "RBAC角色授权接口")
@RestController
@RequestMapping("/api/v1/rbac/roles")
@RequiredArgsConstructor
public class RolePermissionController {

    private final RolePermissionService rolePermissionService;
    private final RoleMapper roleMapper;

    @Operation(summary = "查询角色权限")
    @GetMapping("/{roleId}/permissions")
    public Result<RolePermissionVO> getRolePermissions(@PathVariable Long roleId) {
        return rolePermissionService.getRolePermissions(roleId);
    }

    @Operation(summary = "保存角色权限")
    @PutMapping("/{roleId}/permissions")
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

    @Operation(summary = "查询角色列表")
    @GetMapping
    public Result<List<RoleVO>> listRoles() {
        List<Role> roles = roleMapper.selectList(new LambdaQueryWrapper<Role>().orderByAsc(Role::getId));
        List<RoleVO> result = roles.stream().map(RoleVO::from).toList();
        return Result.success(result);
    }
}
