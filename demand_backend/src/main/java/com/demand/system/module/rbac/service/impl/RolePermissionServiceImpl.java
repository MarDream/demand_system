package com.demand.system.module.rbac.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.cache.VisibleOrgCache;
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
import com.demand.system.module.rbac.dto.RoleTreeNodeVO;
import com.demand.system.module.rbac.dto.RoleUpdateDTO;
import com.demand.system.module.rbac.dto.RoleVO;
import com.demand.system.module.rbac.entity.Role;
import com.demand.system.module.rbac.entity.RoleGroup;
import com.demand.system.module.rbac.entity.RoleGroupRelation;
import com.demand.system.module.rbac.entity.RoleDataScopeOrg;
import com.demand.system.module.rbac.entity.SysPermission;
import com.demand.system.module.rbac.entity.SysRolePermission;
import com.demand.system.module.rbac.entity.UserRole;
import com.demand.system.module.rbac.mapper.RoleDataScopeOrgMapper;
import com.demand.system.module.rbac.mapper.RoleGroupMapper;
import com.demand.system.module.rbac.mapper.RoleGroupRelationMapper;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
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
    private final RoleDataScopeOrgMapper roleDataScopeOrgMapper;
    private final RoleGroupRelationMapper roleGroupRelationMapper;
    private final VisibleOrgCache visibleOrgCache;

    public RolePermissionServiceImpl(RoleMapper roleMapper, RoleGroupMapper roleGroupMapper, SysPermissionMapper sysPermissionMapper, SysRolePermissionMapper sysRolePermissionMapper, UserRoleMapper userRoleMapper, RbacPermissionResolver rbacPermissionResolver, RoleDataScopeOrgMapper roleDataScopeOrgMapper, RoleGroupRelationMapper roleGroupRelationMapper, VisibleOrgCache visibleOrgCache) {
        this.roleMapper = roleMapper;
        this.roleGroupMapper = roleGroupMapper;
        this.sysPermissionMapper = sysPermissionMapper;
        this.sysRolePermissionMapper = sysRolePermissionMapper;
        this.userRoleMapper = userRoleMapper;
        this.rbacPermissionResolver = rbacPermissionResolver;
        this.roleDataScopeOrgMapper = roleDataScopeOrgMapper;
        this.roleGroupRelationMapper = roleGroupRelationMapper;
        this.visibleOrgCache = visibleOrgCache;
    }

    @Override
    public Result<List<RoleVO>> listRoles() {
        List<Role> roles = roleMapper.selectList(new LambdaQueryWrapper<Role>().orderByAsc(Role::getSortOrder).orderByAsc(Role::getId));
        // 批量查询所有角色的关联分组
        Map<Long, List<Long>> roleGroupMap = loadRoleGroupIdsMap(roles);
        return Result.success(roles.stream().map(role -> {
            RoleVO vo = RoleVO.from(role);
            vo.setGroupIds(roleGroupMap.get(role.getId()));
            return vo;
        }).toList());
    }

    /**
     * 批量加载角色关联分组ID
     */
    private Map<Long, List<Long>> loadRoleGroupIdsMap(List<Role> roles) {
        if (roles.isEmpty()) {
            return Map.of();
        }
        List<Long> roleIds = roles.stream().map(Role::getId).toList();
        List<RoleGroupRelation> relations = roleGroupRelationMapper.selectList(
                new LambdaQueryWrapper<RoleGroupRelation>()
                        .in(RoleGroupRelation::getRoleId, roleIds));
        Map<Long, List<Long>> map = new LinkedHashMap<>();
        for (RoleGroupRelation relation : relations) {
            map.computeIfAbsent(relation.getRoleId(), k -> new ArrayList<>()).add(relation.getRoleGroupId());
        }
        return map;
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
        role.setRoleGroupId(request.getRoleGroupId());
        role.setIsSystem(0);
        // 判断是否默认角色：在默认分组下创建的角色自动标记为默认角色
        boolean isDefaultGroup = false;
        if (request.getRoleGroupId() != null) {
            RoleGroup group = roleGroupMapper.selectById(request.getRoleGroupId());
            isDefaultGroup = group != null && group.getIsDefault() != null && group.getIsDefault() == 1;
        }
        role.setIsDefault(isDefaultGroup ? 1 : 0);
        LocalDateTime now = LocalDateTime.now();
        role.setCreatedAt(now);
        role.setUpdatedAt(now);
        role.setDeletedAt(0);
        roleMapper.insert(role);

        // 写入关联分组
        saveRoleGroupRelations(role.getId(), request.getRoleGroupId(), request.getGroupIds(), now);

        RoleVO result = RoleVO.from(role);
        result.setGroupIds(request.getGroupIds());
        return Result.success(result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<RoleVO> updateRole(RoleUpdateDTO request) {
        requireRoleManagement();
        Role role = getRole(request.getId());
        ensureMutableRole(role);

        // 非默认角色不允许修改关联分组
        boolean isDefaultRole = role.getIsDefault() != null && role.getIsDefault() == 1;
        if (!isDefaultRole && request.getGroupIds() != null && !request.getGroupIds().isEmpty()) {
            throw new BusinessException("非默认角色不能关联到多个分组");
        }

        String code = normalizeCode(request.getCode());
        if (!Objects.equals(role.getCode(), code)) {
            validateRoleCodeUnique(role.getId(), code);
        }
        validateRoleNameUnique(role.getId(), request.getName());
        role.setCode(code);
        role.setName(request.getName().trim());
        role.setDescription(trimToNull(request.getDescription()));
        role.setRoleGroupId(request.getRoleGroupId());
        role.setUpdatedAt(LocalDateTime.now());
        roleMapper.updateById(role);

        // 同步关联分组
        if (request.getGroupIds() != null) {
            saveRoleGroupRelations(role.getId(), request.getRoleGroupId(), request.getGroupIds(), LocalDateTime.now());
        }

        RoleVO result = RoleVO.from(role);
        result.setGroupIds(request.getGroupIds());
        return Result.success(result);
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
        roleGroupRelationMapper.deleteByRoleId(roleId);
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
        Set<Long> dataScopeOrgIds = loadDataScopeOrgIds(roleId);
        result.setDataScopeOrgIds(List.copyOf(dataScopeOrgIds));
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

        // 保存数据权限组织范围
        if (request.getDataScopeOrgIds() != null) {
            roleDataScopeOrgMapper.deleteByRoleId(role.getId());
            if (!request.getDataScopeOrgIds().isEmpty()) {
                LocalDateTime now2 = LocalDateTime.now();
                for (Long orgId : request.getDataScopeOrgIds()) {
                    RoleDataScopeOrg relation = new RoleDataScopeOrg(role.getId(), orgId);
                    relation.setCreatedAt(now2);
                    roleDataScopeOrgMapper.insert(relation);
                }
            }
            // 清除已变更角色对应用户的 VisibleOrgCache
            clearVisibleOrgCacheForRole(role.getId());
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
        // 如果创建的是默认分组，先取消现有默认分组的标记
        if (request.getIsDefault() != null && request.getIsDefault() == 1) {
            List<RoleGroup> existingDefault = roleGroupMapper.selectList(
                    new LambdaQueryWrapper<RoleGroup>().eq(RoleGroup::getIsDefault, 1));
            for (RoleGroup g : existingDefault) {
                g.setIsDefault(0);
                g.setUpdatedAt(LocalDateTime.now());
                roleGroupMapper.updateById(g);
            }
        }

        RoleGroup group = new RoleGroup();
        group.setName(request.getName().trim());
        group.setDescription(trimToNull(request.getDescription()));
        group.setSortOrder(0);
        group.setIsDefault(request.getIsDefault() != null ? request.getIsDefault() : 0);
        LocalDateTime now = LocalDateTime.now();
        group.setCreatedAt(now);
        group.setUpdatedAt(now);
        group.setDeletedAt(0);
        roleGroupMapper.insert(group);

        // Assign roles to the new group
        if (request.getRoleIds() != null && !request.getRoleIds().isEmpty()) {
            for (Long roleId : request.getRoleIds()) {
                Role role = roleMapper.selectById(roleId);
                if (role != null) {
                    role.setRoleGroupId(group.getId());
                    role.setUpdatedAt(now);
                    roleMapper.updateById(role);
                    // 同时写入关联表
                    saveRoleGroupRelation(roleId, group.getId(), now);
                }
            }
        }

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
        // Move roles in this group to default group (set roleGroupId to null)
        List<Role> rolesInGroup = roleMapper.selectList(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleGroupId, roleGroupId));
        if (!rolesInGroup.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            for (Role role : rolesInGroup) {
                role.setRoleGroupId(null);
                role.setUpdatedAt(now);
                roleMapper.updateById(role);
            }
        }
        // 删除关联表记录
        roleGroupRelationMapper.deleteByRoleGroupId(roleGroupId);
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
                boolean groupChanged = !Objects.equals(role.getRoleGroupId(), item.getRoleGroupId());
                role.setRoleGroupId(item.getRoleGroupId());
                role.setSortOrder(item.getSortOrder());
                role.setUpdatedAt(now);
                roleMapper.updateById(role);
                // 主分组变更时同步关联表
                if (groupChanged) {
                    saveRoleGroupRelations(role.getId(), item.getRoleGroupId(), null, now);
                }
            }
        }
        return Result.success();
    }

    private Set<Long> loadDataScopeOrgIds(Long roleId) {
        List<RoleDataScopeOrg> relations = roleDataScopeOrgMapper.selectList(
                new LambdaQueryWrapper<RoleDataScopeOrg>()
                        .eq(RoleDataScopeOrg::getRoleId, roleId));
        return relations.stream()
                .map(RoleDataScopeOrg::getOrgId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private void clearVisibleOrgCacheForRole(Long roleId) {
        List<UserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getRoleId, roleId));
        for (UserRole ur : userRoles) {
            if (ur.getUserId() != null) {
                visibleOrgCache.invalidate(ur.getUserId());
            }
        }
    }

    // ========== 角色分组树 ==========

    @Override
    public Result<List<RoleTreeNodeVO>> getRoleTree() {
        // 查询所有分组
        List<RoleGroup> groups = roleGroupMapper.selectList(
                new LambdaQueryWrapper<RoleGroup>().orderByAsc(RoleGroup::getSortOrder));
        // 查询所有角色
        List<Role> roles = roleMapper.selectList(
                new LambdaQueryWrapper<Role>().orderByAsc(Role::getSortOrder).orderByAsc(Role::getId));
        // 批量查询关联分组
        Map<Long, List<Long>> roleGroupMap = loadRoleGroupIdsMap(roles);

        List<RoleTreeNodeVO> result = new ArrayList<>();

        // 默认分组（无分组角色）：作为第一个节点
        List<RoleTreeNodeVO.RoleItemVO> unassignedRoles = roles.stream()
                .filter(role -> {
                    List<Long> gids = roleGroupMap.get(role.getId());
                    return gids == null || gids.isEmpty();
                })
                .map(role -> buildRoleItemVO(role, roleGroupMap.get(role.getId())))
                .toList();
        RoleTreeNodeVO defaultNode = new RoleTreeNodeVO();
        defaultNode.setGroupId(null);
        defaultNode.setGroupName("默认");
        defaultNode.setIsDefault(1);
        defaultNode.setChildren(unassignedRoles);
        result.add(defaultNode);

        // 各分组节点
        for (RoleGroup group : groups) {
            List<RoleTreeNodeVO.RoleItemVO> groupRoles = roles.stream()
                    .filter(role -> {
                        List<Long> gids = roleGroupMap.get(role.getId());
                        return gids != null && gids.contains(group.getId());
                    })
                    .map(role -> buildRoleItemVO(role, roleGroupMap.get(role.getId())))
                    .toList();

            RoleTreeNodeVO node = new RoleTreeNodeVO();
            node.setGroupId(group.getId());
            node.setGroupName(group.getName());
            node.setIsDefault(group.getIsDefault());
            node.setChildren(groupRoles);
            result.add(node);
        }

        return Result.success(result);
    }

    private RoleTreeNodeVO.RoleItemVO buildRoleItemVO(Role role, List<Long> groupIds) {
        RoleTreeNodeVO.RoleItemVO item = new RoleTreeNodeVO.RoleItemVO();
        item.setId(role.getId());
        item.setName(role.getName());
        item.setCode(role.getCode());
        item.setIsDefault(role.getIsDefault());
        item.setGroupIds(groupIds != null ? groupIds : List.of());
        return item;
    }

    // ========== 关联分组工具方法 ==========

    /**
     * 保存角色关联分组
     * @param roleId 角色ID
     * @param mainGroupId 主分组ID
     * @param groupIds 额外关联分组ID列表（不含主分组）
     * @param now 当前时间
     */
    private void saveRoleGroupRelations(Long roleId, Long mainGroupId, List<Long> groupIds, LocalDateTime now) {
        // 删除旧的关联
        roleGroupRelationMapper.deleteByRoleId(roleId);
        // 插入主分组
        if (mainGroupId != null) {
            saveRoleGroupRelation(roleId, mainGroupId, now);
        }
        // 插入额外分组
        if (groupIds != null) {
            for (Long groupId : groupIds) {
                if (!Objects.equals(groupId, mainGroupId)) {
                    saveRoleGroupRelation(roleId, groupId, now);
                }
            }
        }
    }

    private void saveRoleGroupRelation(Long roleId, Long groupId, LocalDateTime now) {
        RoleGroupRelation relation = new RoleGroupRelation();
        relation.setRoleId(roleId);
        relation.setRoleGroupId(groupId);
        relation.setCreatedAt(now);
        roleGroupRelationMapper.insert(relation);
    }
}
