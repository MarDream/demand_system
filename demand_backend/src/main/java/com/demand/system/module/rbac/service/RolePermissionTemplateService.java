package com.demand.system.module.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.module.rbac.entity.Role;
import com.demand.system.module.rbac.entity.SysPermission;
import com.demand.system.module.rbac.entity.SysRolePermission;
import com.demand.system.module.rbac.mapper.SysPermissionMapper;
import com.demand.system.module.rbac.mapper.SysRolePermissionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 角色权限模板服务
 * 为不同类型的角色预设标准权限包
 */
@Service
public class RolePermissionTemplateService {

    private static final Logger log = LoggerFactory.getLogger(RolePermissionTemplateService.class);

    private final SysPermissionMapper permissionMapper;
    private final SysRolePermissionMapper rolePermissionMapper;

    public RolePermissionTemplateService(SysPermissionMapper permissionMapper,
                                        SysRolePermissionMapper rolePermissionMapper) {
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
    }

    /**
     * 角色权限模板定义
     * Key: 角色代码
     * Value: 权限代码列表
     */
    private static final Map<String, List<String>> ROLE_PERMISSION_TEMPLATES = Map.of(
        // 运维需求分析员
        "DEMAND_OPS", List.of(
            // 菜单权限
            "menu:requirement",
            "menu:requirement:view:all",
            "menu:requirement:view:pending",
            "menu:requirement:view:done",
            "menu:requirement:view:follow",
            // 按钮权限
            "button:requirement:submit",      // 提交/流转需求（必需）
            "button:requirement:comment",     // 评论需求
            "button:requirement:rollback"     // 回退需求
        ),

        // 业务需求分析员
        "DEMAND_ANALYST", List.of(
            // 菜单权限
            "menu:requirement",
            "menu:requirement:view:all",
            "menu:requirement:view:pending",
            "menu:requirement:view:done",
            "menu:requirement:view:draft",
            "menu:requirement:view:follow",
            // 按钮权限
            "button:requirement:create",      // 新建需求
            "button:requirement:submit",      // 提交/流转需求（必需）
            "button:requirement:update",      // 编辑需求
            "button:requirement:draft",       // 保存草稿
            "button:requirement:comment",     // 评论需求
            "button:requirement:split"        // 拆分需求
        ),

        // 开发人员
        "DEVELOPER", List.of(
            // 菜单权限
            "menu:requirement",
            "menu:requirement:view:pending",
            "menu:requirement:view:done",
            // 按钮权限
            "button:requirement:submit",      // 提交/流转需求（必需）
            "button:requirement:comment"      // 评论需求
        ),

        // 测试人员
        "TESTER", List.of(
            // 菜单权限
            "menu:requirement",
            "menu:requirement:view:pending",
            "menu:requirement:view:done",
            // 按钮权限
            "button:requirement:submit",      // 提交/流转需求（必需）
            "button:requirement:comment"      // 评论需求
        ),

        // 产品经理
        "PRODUCT_MANAGER", List.of(
            // 菜单权限
            "menu:requirement",
            "menu:requirement:view:all",
            "menu:requirement:view:pending",
            "menu:requirement:view:done",
            "menu:requirement:view:draft",
            "menu:requirement:view:follow",
            // 按钮权限
            "button:requirement:create",
            "button:requirement:submit",
            "button:requirement:update",
            "button:requirement:draft",
            "button:requirement:comment",
            "button:requirement:split",
            "button:requirement:cancel"
        ),

        // 项目经理
        "PROJECT_MANAGER", List.of(
            // 菜单权限
            "menu:requirement",
            "menu:requirement:view:all",
            "menu:requirement:view:pending",
            "menu:requirement:view:done",
            "menu:requirement:view:follow",
            // 按钮权限
            "button:requirement:submit",
            "button:requirement:comment",
            "button:requirement:export"
        )
    );

    /**
     * 为角色应用权限模板
     *
     * @param role 角色实体
     * @return 添加的权限数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int applyTemplateToRole(Role role) {
        String roleCode = role.getCode();
        List<String> permissionCodes = ROLE_PERMISSION_TEMPLATES.get(roleCode);

        if (permissionCodes == null || permissionCodes.isEmpty()) {
            log.debug("角色 [{}] 没有预设权限模板", roleCode);
            return 0;
        }

        log.info("为角色 [{}({})] 应用权限模板，包含 {} 个权限",
                 role.getName(), roleCode, permissionCodes.size());

        int addedCount = 0;

        for (String permissionCode : permissionCodes) {
            // 查询权限
            SysPermission permission = permissionMapper.selectOne(
                new LambdaQueryWrapper<SysPermission>()
                    .eq(SysPermission::getCode, permissionCode)
            );

            if (permission == null) {
                log.warn("权限 [{}] 不存在，跳过", permissionCode);
                continue;
            }

            // 检查是否已存在
            Long count = rolePermissionMapper.selectCount(
                new LambdaQueryWrapper<SysRolePermission>()
                    .eq(SysRolePermission::getRoleId, role.getId())
                    .eq(SysRolePermission::getPermissionId, permission.getId())
            );

            if (count > 0) {
                log.debug("权限 [{}] 已存在，跳过", permissionCode);
                continue;
            }

            // 插入权限关联
            SysRolePermission rp = new SysRolePermission();
            rp.setRoleId(role.getId());
            rp.setPermissionId(permission.getId());
            rolePermissionMapper.insert(rp);
            addedCount++;

            log.debug("为角色 [{}] 添加权限 [{}({})]", roleCode, permission.getName(), permissionCode);
        }

        log.info("为角色 [{}({})] 成功添加 {} 个权限", role.getName(), roleCode, addedCount);
        return addedCount;
    }

    /**
     * 批量修复：为所有符合模板的角色添加缺失权限
     *
     * @return 修复的角色数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int batchFixRolePermissions(List<Role> roles) {
        int fixedRoleCount = 0;

        for (Role role : roles) {
            if (ROLE_PERMISSION_TEMPLATES.containsKey(role.getCode())) {
                int addedCount = applyTemplateToRole(role);
                if (addedCount > 0) {
                    fixedRoleCount++;
                }
            }
        }

        log.info("批量修复完成，共修复 {} 个角色的权限配置", fixedRoleCount);
        return fixedRoleCount;
    }

    /**
     * 检查角色是否缺少模板中的权限
     *
     * @param role 角色实体
     * @return 缺少的权限代码列表
     */
    public List<String> checkMissingPermissions(Role role) {
        String roleCode = role.getCode();
        List<String> templatePermissions = ROLE_PERMISSION_TEMPLATES.get(roleCode);

        if (templatePermissions == null || templatePermissions.isEmpty()) {
            return List.of();
        }

        // 查询角色已有的权限
        List<String> existingPermissionCodes = rolePermissionMapper.selectList(
            new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, role.getId())
        ).stream()
         .map(rp -> {
             SysPermission p = permissionMapper.selectById(rp.getPermissionId());
             return p != null ? p.getCode() : null;
         })
         .filter(code -> code != null)
         .toList();

        // 找出缺少的权限
        return templatePermissions.stream()
            .filter(code -> !existingPermissionCodes.contains(code))
            .toList();
    }

    /**
     * 获取所有支持模板的角色代码
     */
    public List<String> getSupportedRoleCodes() {
        return List.copyOf(ROLE_PERMISSION_TEMPLATES.keySet());
    }

    /**
     * 获取指定角色的模板权限列表
     */
    public List<String> getTemplatePermissions(String roleCode) {
        return ROLE_PERMISSION_TEMPLATES.getOrDefault(roleCode, List.of());
    }
}
