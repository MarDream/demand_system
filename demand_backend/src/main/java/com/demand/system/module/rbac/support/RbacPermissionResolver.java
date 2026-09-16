package com.demand.system.module.rbac.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.module.rbac.entity.Role;
import com.demand.system.module.rbac.entity.SysPermission;
import com.demand.system.module.rbac.entity.SysRolePermission;
import com.demand.system.module.rbac.entity.UserRole;
import com.demand.system.module.rbac.mapper.RoleMapper;
import com.demand.system.module.rbac.mapper.SysPermissionMapper;
import com.demand.system.module.rbac.mapper.SysRolePermissionMapper;
import com.demand.system.module.rbac.mapper.UserRoleMapper;
import com.demand.system.module.user.entity.UserOrganization;
import com.demand.system.module.user.mapper.UserOrganizationMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class RbacPermissionResolver {

    /** 角色切换功能：前端每次请求携带的当前生效角色请求头。 */
    public static final String ACTIVE_ROLE_HEADER = "X-Active-Role";

    private final UserOrganizationMapper userOrganizationMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final SysRolePermissionMapper sysRolePermissionMapper;
    private final SysPermissionMapper sysPermissionMapper;

    public RbacPermissionResolver(UserOrganizationMapper userOrganizationMapper, UserRoleMapper userRoleMapper, RoleMapper roleMapper, SysRolePermissionMapper sysRolePermissionMapper, SysPermissionMapper sysPermissionMapper) {
        this.userOrganizationMapper = userOrganizationMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.sysRolePermissionMapper = sysRolePermissionMapper;
        this.sysPermissionMapper = sysPermissionMapper;
    }

    public List<String> resolveRoles(Long userId) {
        LinkedHashSet<String> roles = new LinkedHashSet<>(resolveLegacyRoles(userId));
        roles.addAll(resolveRoleCodesFromUserRoles(userId));
        if (roles.isEmpty()) {
            roles.add("USER");
        }
        return List.copyOf(roles);
    }

    /**
     * 按激活角色过滤角色列表（角色切换功能）。
     * 仅当 activeRole 确实属于该用户的角色之一时才收窄到该角色；
     * 否则忽略该值，保持全部角色，防止伪造请求头越权。
     */
    public List<String> resolveRoles(Long userId, String activeRole) {
        List<String> roles = resolveRoles(userId);
        if (!StringUtils.hasText(activeRole)) {
            return roles;
        }
        String candidate = activeRole.trim();
        return roles.contains(candidate) ? List.of(candidate) : roles;
    }

    /** 读取当前请求的激活角色请求头；不在请求上下文中（如定时任务）时返回 null。 */
    public String currentActiveRole() {
        org.springframework.web.context.request.RequestAttributes attrs =
                org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest().getHeader(ACTIVE_ROLE_HEADER);
        }
        return null;
    }

    /** 激活角色是否真实生效（属于该用户且非空）。 */
    public boolean isActiveRoleApplied(Long userId, String activeRole) {
        return StringUtils.hasText(activeRole) && resolveRoles(userId).contains(activeRole.trim());
    }

    /** 用户的角色编码 → 显示名映射（优先 roles 表中文名，legacy 体系角色兜底内置中文名）。 */
    public Map<String, String> resolveRoleNameMap(Long userId) {
        Map<String, String> names = new LinkedHashMap<>();
        List<String> legacyCodes = resolveLegacyRoles(userId);
        for (String legacy : legacyCodes) {
            names.put(legacy, legacy);
        }
        for (Role role : loadRolesByUserId(userId)) {
            String code = role.getCode();
            if (StringUtils.hasText(code)) {
                names.put(code, StringUtils.hasText(role.getName()) ? role.getName() : code);
            }
        }
        // legacy 编码在 roles 表中无对应行时，用内置中文名兜底，避免把编码直接展示给用户
        if (!legacyCodes.isEmpty()) {
            List<Role> matched = roleMapper.selectList(new LambdaQueryWrapper<Role>()
                    .in(Role::getCode, legacyCodes)
                    .eq(Role::getDeletedAt, 0));
            for (Role role : matched) {
                if (StringUtils.hasText(role.getCode()) && StringUtils.hasText(role.getName())) {
                    names.put(role.getCode(), role.getName());
                }
            }
            for (String legacy : legacyCodes) {
                if (legacy.equals(names.get(legacy))) {
                    names.put(legacy, LEGACY_ROLE_DISPLAY_NAMES.getOrDefault(legacy, legacy));
                }
            }
        }
        // 无任何角色时 resolveRoles 会兜底 USER，这里同步给中文名
        names.putIfAbsent("USER", "普通用户");
        return names;
    }

    /** legacy 体系角色编码的中文显示名（roles 表无对应行时的兜底）。 */
    private static final Map<String, String> LEGACY_ROLE_DISPLAY_NAMES = Map.of(
            RbacConstants.ROLE_ADMIN, "超级管理员",
            "super_admin", "超级管理员",
            RbacConstants.ROLE_SUPER_ADMIN_DB, "超级管理员",
            RbacConstants.ROLE_WORKFLOW_CONFIG, "流程配置",
            "USER", "普通用户"
    );

    public List<String> resolvePermissions(Long userId, Collection<String> roles) {
        LinkedHashSet<String> permissions = new LinkedHashSet<>();
        LinkedHashSet<String> normalizedRoles = roles == null
                ? new LinkedHashSet<>()
                : roles.stream().filter(StringUtils::hasText).collect(Collectors.toCollection(LinkedHashSet::new));

        if (isSuperAdmin(normalizedRoles)) {
            permissions.addAll(RbacConstants.ALL_PERMISSION_CODES);
            return List.copyOf(permissions);
        }

        permissions.addAll(resolvePermissionsFromRoles(normalizedRoles));
        permissions.addAll(resolvePermissionsFromLegacyAuthorities(normalizedRoles));
        return List.copyOf(permissions);
    }

    public List<String> resolveRoleDisplayNames(Long userId) {
        List<String> roles = resolveRoles(userId);
        Map<String, String> nameMap = resolveRoleNameMap(userId);
        return roles.stream()
                .map(code -> nameMap.getOrDefault(code, code))
                .toList();
    }

    public boolean isSuperAdmin(Collection<String> roles) {
        if (roles == null) {
            return false;
        }
        return roles.stream().anyMatch(role -> Objects.equals(role, RbacConstants.ROLE_SUPER_ADMIN)
                || Objects.equals(role, RbacConstants.ROLE_SUPER_ADMIN_DB));
    }

    private List<String> resolveLegacyRoles(Long userId) {
        UserOrganization org = userOrganizationMapper.selectOne(
                new LambdaQueryWrapper<UserOrganization>()
                        .eq(UserOrganization::getUserId, userId)
                        .last("LIMIT 1")
        );
        if (org == null || !StringUtils.hasText(org.getSystemRole())) {
            return List.of();
        }
        return Arrays.stream(org.getSystemRole().split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private List<String> resolveRoleCodesFromUserRoles(Long userId) {
        List<Role> roles = loadRolesByUserId(userId);
        return roles.stream()
                .map(Role::getCode)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private List<Role> loadRolesByUserId(Long userId) {
        List<UserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId)
        );
        if (userRoles.isEmpty()) {
            return List.of();
        }
        Set<Long> roleIds = userRoles.stream()
                .map(UserRole::getRoleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectBatchIds(roleIds);
    }

    private List<String> resolvePermissionsFromRoles(Set<String> roleCodes) {
        if (roleCodes.isEmpty()) {
            return List.of();
        }
        List<Role> roles = roleMapper.selectList(new LambdaQueryWrapper<Role>()
                .in(Role::getCode, roleCodes)
                .eq(Role::getDeletedAt, 0));
        if (roles.isEmpty()) {
            return List.of();
        }
        Set<Long> roleIds = roles.stream().map(Role::getId).collect(Collectors.toSet());
        List<SysRolePermission> relations = sysRolePermissionMapper.selectList(
                new LambdaQueryWrapper<SysRolePermission>().in(SysRolePermission::getRoleId, roleIds)
        );
        if (relations.isEmpty()) {
            return List.of();
        }
        Set<Long> permissionIds = relations.stream()
                .map(SysRolePermission::getPermissionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (permissionIds.isEmpty()) {
            return List.of();
        }
        List<SysPermission> permissions = sysPermissionMapper.selectBatchIds(permissionIds);
        return permissions.stream()
                .map(SysPermission::getCode)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private List<String> resolvePermissionsFromLegacyAuthorities(Set<String> roles) {
        if (roles.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> permissions = new LinkedHashSet<>();
        if (roles.contains(RbacConstants.ROLE_ADMIN)) {
            permissions.addAll(List.of(
                    RbacConstants.PERMISSION_MENU_SYSTEM_CONFIG,
                    RbacConstants.PERMISSION_MENU_SETTINGS_PROJECT,
                    RbacConstants.PERMISSION_MENU_SETTINGS_USER,
                    RbacConstants.PERMISSION_MENU_SETTINGS_REQUIREMENT,
                    RbacConstants.PERMISSION_MENU_SETTINGS_WORKFLOW,
                    RbacConstants.PERMISSION_MENU_SETTINGS_ROLE,
                    RbacConstants.PERMISSION_MENU_MANAGEMENT,
                    RbacConstants.PERMISSION_MENU_DOCUMENT,
                    RbacConstants.PERMISSION_MENU_KNOWLEDGE,
                    RbacConstants.PERMISSION_BUTTON_MENU_CREATE,
                    RbacConstants.PERMISSION_BUTTON_MENU_UPDATE,
                    RbacConstants.PERMISSION_BUTTON_MENU_DELETE,
                    RbacConstants.PERMISSION_BUTTON_MENU_GRANT,
                    RbacConstants.PERMISSION_BUTTON_USER_CREATE,
                    RbacConstants.PERMISSION_BUTTON_USER_UPDATE,
                    RbacConstants.PERMISSION_BUTTON_USER_DELETE,
                    RbacConstants.PERMISSION_BUTTON_ROLE_CREATE,
                    RbacConstants.PERMISSION_BUTTON_ROLE_UPDATE,
                    RbacConstants.PERMISSION_BUTTON_ROLE_DELETE,
                    RbacConstants.PERMISSION_BUTTON_ROLE_GRANT,
                    RbacConstants.PERMISSION_BUTTON_WORKFLOW_CONFIG,
                    RbacConstants.PERMISSION_BUTTON_RAG_UPLOAD,
                    RbacConstants.PERMISSION_BUTTON_RAG_SEARCH
            ));
        }
        if (roles.contains(RbacConstants.ROLE_WORKFLOW_CONFIG)) {
            permissions.add(RbacConstants.PERMISSION_MENU_SETTINGS_WORKFLOW);
            permissions.add(RbacConstants.PERMISSION_BUTTON_WORKFLOW_CONFIG);
        }
        return List.copyOf(permissions);
    }

    public Map<String, SysPermission> loadPermissionMapByCodes(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Map.of();
        }
        return sysPermissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                        .in(SysPermission::getCode, codes))
                .stream()
                .collect(Collectors.toMap(SysPermission::getCode, Function.identity(), (left, right) -> left));
    }
}
