package com.demand.system.module.rbac.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
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
import com.demand.system.module.rbac.entity.Role;
import com.demand.system.module.rbac.entity.RoleGroup;
import com.demand.system.module.rbac.entity.SysPermission;
import com.demand.system.module.rbac.entity.SysRolePermission;
import com.demand.system.module.rbac.entity.UserRole;
import com.demand.system.module.rbac.mapper.RoleGroupMapper;
import com.demand.system.module.rbac.mapper.RoleMapper;
import com.demand.system.module.rbac.mapper.SysPermissionMapper;
import com.demand.system.module.rbac.mapper.SysRolePermissionMapper;
import com.demand.system.module.rbac.mapper.UserRoleMapper;
import com.demand.system.module.rbac.service.RolePermissionService;
import com.demand.system.module.rbac.support.RbacConstants;
import com.demand.system.module.rbac.support.RbacPermissionResolver;
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
public class RolePermissionServiceImpl implements RolePermissionService {

    private final RoleMapper roleMapper;
    private final RoleGroupMapper roleGroupMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final SysRolePermissionMapper sysRolePermissionMapper;
    private final UserRoleMapper userRoleMapper;
    private final RbacPermissionResolver rbacPermissionResolver;

    public RolePermissionServiceImpl(RoleMapper roleMapper, RoleGroupMapper roleGroupMapper, SysPermissionMapper sysPermissionMapper, SysRolePermissionMapper sysRolePermissionMapper, UserRoleMapper userRoleMapper, RbacPermissionResolver rbacPermissionResolver) {
        this.roleMapper = roleMapper;
        this.roleGroupMapper = roleGroupMapper;
        this.sysPermissionMapper = sysPermissionMapper;
        this.sysRolePermissionMapper = sysRolePermissionMapper;
        this.userRoleMapper = userRoleMapper;
        this.rbacPermissionResolver = rbacPermissionResolver;
    }

    @Override
    public Result<List<RoleVO>> listRoles() {
        List<Role> roles = roleMapper.selectList(new LambdaQueryWrapper<Role>().orderByAsc(Role::getId));
        return Result.success(roles.stream().map(RoleVO::from).toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<RoleVO> createRole(RoleCreateDTO request) {
        requireRoleManagement();
        String code = normalizeCode(request.getCode());
        validateRoleCodeUnique(null, code);
        validateRoleNameUnique(null, request.getName());

        Role role = new Role();
        role.setCode(code);
        role.setName(request.getName().trim());
        role.setDescription(trimToNull(request.getDescription()));
        role.setIsSystem(0);
        LocalDateTime now = LocalDateTime.now();
        role.setCreatedAt(now);
        role.setUpdatedAt(now);
        role.setDeletedAt(0);
        roleMapper.insert(role);
        return Result.success(RoleVO.from(role));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<RoleVO> updateRole(RoleUpdateDTO request) {
        requireRoleManagement();
        Role role = getRole(request.getId());
        ensureMutableRole(role);

        String code = normalizeCode(request.getCode());
        if (!Objects.equals(role.getCode(), code)) {
            validateRoleCodeUnique(role.getId(), code);
        }
        validateRoleNameUnique(role.getId(), request.getName());
        role.setCode(code);
        role.setName(request.getName().trim());
        role.setDescription(trimToNull(request.getDescription()));
        role.setUpdatedAt(LocalDateTime.now());
        roleMapper.updateById(role);
        return Result.success(RoleVO.from(role));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteRole(Long roleId) {
        requireRoleManagement();
        Role role = getRole(roleId);
        ensureMutableRole(role);

        long userCount = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getRoleId, roleId));
        if (userCount > 0) {
            throw new BusinessException("该角色已分配给用户，不能删除");
        }

        sysRolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, roleId));
        roleMapper.deleteById(roleId);
        return Result.success();
    }

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
        if (Objects.equals(role.getCode(), RbacConstants.ROLE_SUPER_ADMIN)
                || Objects.equals(role.getCode(), RbacConstants.ROLE_SUPER_ADMIN_DB)) {
            throw new BusinessException("不能修改超级管理员角色权限");
        }
        if (superAdmin) {
            return;
        }
        if (!currentPermissions.contains(RbacConstants.PERMISSION_BUTTON_MENU_GRANT)
                && !currentPermissions.contains(RbacConstants.PERMISSION_MENU_MANAGEMENT)
                && !currentPermissions.contains(RbacConstants.PERMISSION_BUTTON_ROLE_GRANT)
                && !currentPermissions.contains(RbacConstants.PERMISSION_MENU_SETTINGS_ROLE)) {
            throw new BusinessException("无权限进行角色授权");
        }
        if (RbacConstants.PROTECTED_ROLE_CODES.contains(role.getCode())) {
            throw new BusinessException("不能修改内置关键角色");
        }
    }

    private void requireRoleManagement() {
        Long currentUserId = requireCurrentUserId();
        List<String> currentRoles = rbacPermissionResolver.resolveRoles(currentUserId);
        if (rbacPermissionResolver.isSuperAdmin(currentRoles)) {
            return;
        }
        Set<String> currentPermissions = new LinkedHashSet<>(rbacPermissionResolver.resolvePermissions(currentUserId, currentRoles));
        if (!currentPermissions.contains(RbacConstants.PERMISSION_MENU_SETTINGS_ROLE)) {
            throw new BusinessException("无权限执行角色管理操作");
        }
    }

    private void ensureMutableRole(Role role) {
        if (role.getIsSystem() != null && role.getIsSystem() == 1) {
            throw new BusinessException("不能修改系统内置角色");
        }
        if (RbacConstants.PROTECTED_ROLE_CODES.contains(role.getCode())) {
            throw new BusinessException("不能修改内置关键角色");
        }
    }

    private void validateRoleCodeUnique(Long roleId, String code) {
        List<Role> exists = roleMapper.selectList(new LambdaQueryWrapper<Role>()
                .eq(Role::getCode, code));
        boolean conflict = exists.stream().anyMatch(item -> !Objects.equals(item.getId(), roleId));
        if (conflict) {
            throw new BusinessException("角色编码已存在");
        }
    }

    private void validateRoleNameUnique(Long roleId, String name) {
        String normalizedName = name == null ? null : name.trim();
        List<Role> exists = roleMapper.selectList(new LambdaQueryWrapper<Role>()
                .eq(Role::getName, normalizedName));
        boolean conflict = exists.stream().anyMatch(item -> !Objects.equals(item.getId(), roleId));
        if (conflict) {
            throw new BusinessException("角色名称已存在");
        }
    }

    private String normalizeCode(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private Long requireCurrentUserId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException("未获取到当前用户");
        }
        return userId;
    }

    @Override
    public Result<List<RoleGroupVO>> listRoleGroups() {
        List<RoleGroup> groups = roleGroupMapper.selectList(
                new LambdaQueryWrapper<RoleGroup>().orderByAsc(RoleGroup::getSortOrder));
        return Result.success(groups.stream().map(RoleGroupVO::from).toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<RoleGroupVO> createRoleGroup(RoleGroupCreateDTO request) {
        RoleGroup group = new RoleGroup();
        group.setName(request.getName().trim());
        group.setDescription(trimToNull(request.getDescription()));
        group.setSortOrder(0);
        LocalDateTime now = LocalDateTime.now();
        group.setCreatedAt(now);
        group.setUpdatedAt(now);
        group.setDeletedAt(0);
        roleGroupMapper.insert(group);
        return Result.success(RoleGroupVO.from(group));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<RoleGroupVO> updateRoleGroup(RoleGroupUpdateDTO request) {
        RoleGroup group = roleGroupMapper.selectById(request.getId());
        if (group == null) {
            throw new BusinessException("角色组不存在");
        }
        group.setName(request.getName().trim());
        group.setDescription(trimToNull(request.getDescription()));
        group.setUpdatedAt(LocalDateTime.now());
        roleGroupMapper.updateById(group);
        return Result.success(RoleGroupVO.from(group));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteRoleGroup(Long roleGroupId) {
        RoleGroup group = roleGroupMapper.selectById(roleGroupId);
        if (group == null) {
            throw new BusinessException("角色组不存在");
        }
        long roleCount = roleMapper.selectCount(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleGroupId, roleGroupId));
        if (roleCount > 0) {
            throw new BusinessException("该角色组下还有角色，不能删除");
        }
        roleGroupMapper.deleteById(roleGroupId);
        return Result.success();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> batchSortRoleGroups(List<RoleGroupSortItem> items) {
        if (items == null || items.isEmpty()) {
            return Result.success();
        }
        LocalDateTime now = LocalDateTime.now();
        for (RoleGroupSortItem item : items) {
            RoleGroup group = roleGroupMapper.selectById(item.getId());
            if (group != null) {
                group.setSortOrder(item.getSortOrder());
                group.setUpdatedAt(now);
                roleGroupMapper.updateById(group);
            }
        }
        return Result.success();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> batchSortRoles(List<RoleSortItem> items) {
        if (items == null || items.isEmpty()) {
            return Result.success();
        }
        LocalDateTime now = LocalDateTime.now();
        for (RoleSortItem item : items) {
            Role role = roleMapper.selectById(item.getId());
            if (role != null) {
                role.setRoleGroupId(item.getRoleGroupId());
                role.setSortOrder(item.getSortOrder());
                role.setUpdatedAt(now);
                roleMapper.updateById(role);
            }
        }
        return Result.success();
    }
}
