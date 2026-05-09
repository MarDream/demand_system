package com.demand.system.module.rbac.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.rbac.dto.RolePermissionSaveDTO;
import com.demand.system.module.rbac.dto.RolePermissionVO;
import com.demand.system.module.rbac.entity.Role;
import com.demand.system.module.rbac.entity.SysPermission;
import com.demand.system.module.rbac.entity.SysRolePermission;
import com.demand.system.module.rbac.mapper.RoleMapper;
import com.demand.system.module.rbac.mapper.SysPermissionMapper;
import com.demand.system.module.rbac.mapper.SysRolePermissionMapper;
import com.demand.system.module.rbac.service.RolePermissionService;
import com.demand.system.module.rbac.support.RbacConstants;
import com.demand.system.module.rbac.support.RbacPermissionResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RolePermissionServiceImpl implements RolePermissionService {

    private final RoleMapper roleMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final SysRolePermissionMapper sysRolePermissionMapper;
    private final RbacPermissionResolver rbacPermissionResolver;

    @Override
    public Result<RolePermissionVO> getRolePermissions(Long roleId) {
        Role role = getRole(roleId);
        Long currentUserId = requireCurrentUserId();
        List<String> currentRoles = rbacPermissionResolver.resolveRoles(currentUserId);
        Set<String> currentPermissions = new LinkedHashSet<>(rbacPermissionResolver.resolvePermissions(currentUserId, currentRoles));
        Set<String> rolePermissions = loadRolePermissionCodes(roleId);
        RolePermissionVO result = RolePermissionVO.builder()
                .roleId(role.getId())
                .roleCode(role.getCode())
                .roleName(role.getName())
                .permissionCodes(List.copyOf(rolePermissions))
                .grantablePermissionCodes(resolveGrantablePermissionCodes(currentRoles, currentPermissions))
                .build();
        return Result.success(result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> saveRolePermissions(RolePermissionSaveDTO request) {
        Role role = getRole(request.getRoleId());
        Long currentUserId = requireCurrentUserId();
        List<String> currentRoles = rbacPermissionResolver.resolveRoles(currentUserId);
        Set<String> currentPermissions = new LinkedHashSet<>(rbacPermissionResolver.resolvePermissions(currentUserId, currentRoles));
        boolean superAdmin = rbacPermissionResolver.isSuperAdmin(currentRoles);

        ensureAssignableRole(role, superAdmin, currentPermissions);

        Set<String> targetCodes = request.getPermissionCodes() == null
                ? Set.of()
                : request.getPermissionCodes().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, SysPermission> permissionMap = rbacPermissionResolver.loadPermissionMapByCodes(targetCodes);
        if (permissionMap.size() != targetCodes.size()) {
            throw new BusinessException("存在无效的权限编码");
        }

        if (!superAdmin && !currentPermissions.containsAll(targetCodes)) {
            throw new BusinessException("只能授予自身已有的权限");
        }

        sysRolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, role.getId()));

        if (!targetCodes.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            for (String code : targetCodes) {
                SysRolePermission relation = new SysRolePermission();
                relation.setRoleId(role.getId());
                relation.setPermissionId(permissionMap.get(code).getId());
                relation.setGrantedBy(currentUserId);
                relation.setCreatedAt(now);
                sysRolePermissionMapper.insert(relation);
            }
        }
        return Result.success();
    }

    @Override
    public Result<List<String>> getCurrentGrantablePermissionCodes() {
        Long currentUserId = requireCurrentUserId();
        List<String> currentRoles = rbacPermissionResolver.resolveRoles(currentUserId);
        Set<String> currentPermissions = new LinkedHashSet<>(rbacPermissionResolver.resolvePermissions(currentUserId, currentRoles));
        return Result.success(resolveGrantablePermissionCodes(currentRoles, currentPermissions));
    }

    private Role getRole(Long roleId) {
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        return role;
    }

    private Set<String> loadRolePermissionCodes(Long roleId) {
        List<SysRolePermission> relations = sysRolePermissionMapper.selectList(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, roleId));
        if (relations.isEmpty()) {
            return new LinkedHashSet<>();
        }
        Set<Long> permissionIds = relations.stream()
                .map(SysRolePermission::getPermissionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (permissionIds.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return sysPermissionMapper.selectBatchIds(permissionIds).stream()
                .map(SysPermission::getCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<String> resolveGrantablePermissionCodes(Collection<String> currentRoles, Set<String> currentPermissions) {
        if (rbacPermissionResolver.isSuperAdmin(currentRoles)) {
            return sysPermissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                            .eq(SysPermission::getStatus, 1)
                            .orderByAsc(SysPermission::getId))
                    .stream()
                    .map(SysPermission::getCode)
                    .filter(Objects::nonNull)
                    .toList();
        }
        return List.copyOf(currentPermissions);
    }

    private void ensureAssignableRole(Role role, boolean superAdmin, Set<String> currentPermissions) {
        if (superAdmin) {
            return;
        }
        if (!currentPermissions.contains(RbacConstants.PERMISSION_BUTTON_MENU_GRANT)
                && !currentPermissions.contains(RbacConstants.PERMISSION_MENU_MANAGEMENT)) {
            throw new BusinessException("无权限进行角色授权");
        }
        if (RbacConstants.PROTECTED_ROLE_CODES.contains(role.getCode())) {
            throw new BusinessException("不能修改内置关键角色");
        }
    }

    private Long requireCurrentUserId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException("未获取到当前用户");
        }
        return userId;
    }
}
