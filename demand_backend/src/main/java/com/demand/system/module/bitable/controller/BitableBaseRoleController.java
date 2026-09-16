package com.demand.system.module.bitable.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.bitable.dto.*;
import com.demand.system.module.bitable.service.BitableAuthorizationService;
import com.demand.system.module.bitable.service.BitableBaseRoleService;
import com.demand.system.module.bitable.service.BitableFieldPermissionService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 多维表格权限管理控制器
 * <p>
 * 支持系统角色 + 自定义角色，按数据表粒度分配权限（数据权限 / 自动化权限）。
 */
@RestController
@RequestMapping("/api/v1/bitable")
public class BitableBaseRoleController {

    private final BitableBaseRoleService roleService;
    private final BitableAuthorizationService authorizationService;
    private final BitableFieldPermissionService fieldPermissionService;

    public BitableBaseRoleController(BitableBaseRoleService roleService,
                                      BitableAuthorizationService authorizationService,
                                      BitableFieldPermissionService fieldPermissionService) {
        this.roleService = roleService;
        this.authorizationService = authorizationService;
        this.fieldPermissionService = fieldPermissionService;
    }

    /**
     * 查询 Base 下所有角色及其权限配置
     */
    @GetMapping("/bases/{baseId}/roles")
    @PreAuthorize("isAuthenticated()")
    public Result<List<BitableBaseRoleVO>> listRoles(@PathVariable Long baseId,
                                                      @RequestParam(defaultValue = "data") String permissionType) {
        Long userId = SecurityUtils.getCurrentUserId();
        authorizationService.checkManagePermission(baseId, userId);
        return Result.success(roleService.listRolesWithPermissions(baseId, permissionType));
    }

    /**
     * 创建自定义角色
     */
    @PostMapping("/bases/{baseId}/custom-roles")
    @PreAuthorize("isAuthenticated()")
    public Result<BitableBaseCustomRoleCreateDTO> createCustomRole(@PathVariable Long baseId,
                                                                    @Valid @RequestBody BitableBaseCustomRoleCreateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        dto.setBaseId(baseId);
        return Result.success(roleService.createCustomRole(dto, userId));
    }

    /**
     * 更新自定义角色
     */
    @PutMapping("/custom-roles/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> updateCustomRole(@PathVariable Long id,
                                          @Valid @RequestBody BitableBaseCustomRoleUpdateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        roleService.updateCustomRole(id, dto, userId);
        return Result.success();
    }

    /**
     * 删除自定义角色
     */
    @DeleteMapping("/custom-roles/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> deleteCustomRole(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        roleService.deleteCustomRole(id, userId);
        return Result.success();
    }

    /**
     * 添加角色成员
     */
    @PostMapping("/custom-roles/{id}/members")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> addRoleMember(@PathVariable Long id,
                                       @RequestBody BitableBaseCustomRoleMemberDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        dto.setRoleId(id);
        roleService.addRoleMember(dto, userId);
        return Result.success();
    }

    /**
     * 移除角色成员
     */
    @DeleteMapping("/custom-roles/{id}/members")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> removeRoleMember(@PathVariable Long id,
                                          @RequestParam String memberType,
                                          @RequestParam Long memberId) {
        Long userId = SecurityUtils.getCurrentUserId();
        roleService.removeRoleMember(id, memberType, memberId, userId);
        return Result.success();
    }

    /**
     * 设置角色对数据表的权限
     */
    @PutMapping("/bases/{baseId}/role-permissions")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> setRolePermission(@PathVariable Long baseId,
                                           @RequestBody BitableBaseRolePermissionDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        dto.setBaseId(baseId);
        roleService.setRolePermission(dto, userId);
        return Result.success();
    }

    /**
     * 批量设置角色权限（全部表统一设置）
     */
    @PutMapping("/bases/{baseId}/role-permissions/batch")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> batchSetRolePermissions(@PathVariable Long baseId,
                                                 @RequestParam String roleType,
                                                 @RequestParam(required = false) String systemRoleCode,
                                                 @RequestParam(required = false) Long customRoleId,
                                                 @RequestParam String permissionType,
                                                 @RequestParam String permissionLevel) {
        Long userId = SecurityUtils.getCurrentUserId();
        roleService.batchSetRolePermissions(baseId, roleType, systemRoleCode, customRoleId,
                permissionType, permissionLevel, userId);
        return Result.success();
    }

    /**
     * 批量保存多条权限变更（事务内一次性提交）
     */
    @PostMapping("/bases/{baseId}/role-permissions/batch-save")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> batchSaveRolePermissions(@PathVariable Long baseId,
                                                  @RequestBody List<BitableBaseRolePermissionDTO> changes) {
        Long userId = SecurityUtils.getCurrentUserId();
        roleService.batchSaveRolePermissions(baseId, changes, userId);
        return Result.success();
    }

    /**
     * 查询字段级权限配置（某张表或整个 Base）。
     * <p>
     * 只返回非默认（非 editable）的条目；未出现的字段视为「可编辑」。
     *
     * @param tableId 数据表ID，省略则返回整个 Base 的配置
     */
    @GetMapping("/bases/{baseId}/field-permissions")
    @PreAuthorize("isAuthenticated()")
    public Result<List<BitableFieldPermissionDTO>> listFieldPermissions(@PathVariable Long baseId,
                                                                         @RequestParam(required = false) Long tableId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(fieldPermissionService.listFieldPermissions(baseId, tableId, userId));
    }

    /**
     * 批量保存字段级权限变更。
     * <p>
     * 前端只提交本次改动过的条目；{@code permissionLevel = editable} 表示恢复默认（删除配置行）。
     */
    @PostMapping("/bases/{baseId}/field-permissions/batch-save")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> batchSaveFieldPermissions(@PathVariable Long baseId,
                                                   @Valid @RequestBody BitableFieldPermissionBatchDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        fieldPermissionService.batchSaveFieldPermissions(baseId, dto.getChanges(), userId);
        return Result.success();
    }
}
