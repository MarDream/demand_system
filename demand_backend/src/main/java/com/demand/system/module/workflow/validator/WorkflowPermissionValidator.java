package com.demand.system.module.workflow.validator;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.module.rbac.entity.Role;
import com.demand.system.module.rbac.entity.SysPermission;
import com.demand.system.module.rbac.entity.SysRolePermission;
import com.demand.system.module.rbac.mapper.RoleMapper;
import com.demand.system.module.rbac.mapper.SysPermissionMapper;
import com.demand.system.module.rbac.mapper.SysRolePermissionMapper;
import com.demand.system.module.workflow.dto.WorkflowNodeDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 工作流权限验证器
 * 确保工作流节点配置的处理人角色拥有对应的RBAC操作权限
 */
@Component
public class WorkflowPermissionValidator {

    private static final Logger log = LoggerFactory.getLogger(WorkflowPermissionValidator.class);

    private final RoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysRolePermissionMapper rolePermissionMapper;

    public WorkflowPermissionValidator(RoleMapper roleMapper,
                                      SysPermissionMapper permissionMapper,
                                      SysRolePermissionMapper rolePermissionMapper) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
    }

    /**
     * 需求流转所需的按钮权限
     */
    private static final List<String> REQUIRED_PERMISSION_CODES = List.of(
            "button:requirement:submit"  // 提交/流转按钮权限
    );

    /**
     * 验证工作流节点配置的角色是否拥有必要的RBAC权限
     *
     * @param nodes 工作流节点列表
     * @throws BusinessException 当角色缺少必要权限时抛出异常
     */
    public void validateNodesPermissions(List<WorkflowNodeDTO> nodes) {
        log.debug("开始验证工作流节点权限配置，节点数量: {}", nodes.size());

        List<String> validationErrors = new ArrayList<>();

        for (WorkflowNodeDTO node : nodes) {
            // 仅检查需要处理人操作的节点类型
            if (!isOperationalNode(node)) {
                continue;
            }

            String assigneeType = node.getAssigneeType();
            if (assigneeType == null || assigneeType.isEmpty()) {
                log.warn("节点 [{}] 未配置 assigneeType，跳过权限验证", node.getNodeName());
                continue;
            }

            // 根据处理人类型进行不同的权限验证
            switch (assigneeType) {
                case "SPECIFIED_ROLE":
                    validateSpecifiedRolePermission(node, validationErrors);
                    break;
                case "SPECIFIED_ROLE_GROUP":
                    validateSpecifiedRoleGroupPermission(node, validationErrors);
                    break;
                case "SPECIFIED_USER":
                case "CREATOR":
                case "PREV_APPROVER":
                case "SPECIFIED_ORG":
                    // 这些类型无法在配置时静态验证，运行时动态检查
                    log.debug("节点 [{}] 使用 {} 类型，跳过静态权限验证", node.getNodeName(), assigneeType);
                    break;
                default:
                    log.warn("节点 [{}] 使用未知的 assigneeType: {}", node.getNodeName(), assigneeType);
            }
        }

        // 如果有验证错误，抛出异常
        if (!validationErrors.isEmpty()) {
            String errorMessage = "工作流权限配置不完整：\n" + String.join("\n", validationErrors);
            log.error("工作流权限验证失败: {}", errorMessage);
            throw new BusinessException(ErrorCode.BAD_REQUEST, errorMessage);
        }

        log.debug("工作流节点权限配置验证通过");
    }

    /**
     * 判断节点是否需要用户操作（排除开始节点、结束节点等）
     */
    private boolean isOperationalNode(WorkflowNodeDTO node) {
        String nodeType = node.getNodeType();
        return "approval".equals(nodeType)
            || "task".equals(nodeType)
            || "review".equals(nodeType);
    }

    /**
     * 验证指定角色类型节点的权限
     */
    private void validateSpecifiedRolePermission(WorkflowNodeDTO node, List<String> errors) {
        Integer roleId = node.getAssigneeRoleId();
        if (roleId == null) {
            errors.add(String.format("• 节点 [%s] 使用 SPECIFIED_ROLE 但未配置 assigneeRoleId",
                                    node.getNodeName()));
            return;
        }

        Role role = roleMapper.selectById(roleId.longValue());
        if (role == null) {
            errors.add(String.format("• 节点 [%s] 配置的角色ID [%d] 不存在",
                                    node.getNodeName(), roleId));
            return;
        }

        if (role.getDeletedAt() != 0) {
            errors.add(String.format("• 节点 [%s] 配置的角色 [%s] 已被删除",
                                    node.getNodeName(), role.getName()));
            return;
        }

        // 检查角色是否拥有必要的权限
        List<String> missingPermissions = checkRoleMissingPermissions(role.getId());
        if (!missingPermissions.isEmpty()) {
            errors.add(String.format("• 节点 [%s] 配置的角色 [%s(%s)] 缺少权限: %s\n  " +
                                    "请在【系统管理 > 角色管理】中为该角色添加上述权限",
                                    node.getNodeName(),
                                    role.getName(),
                                    role.getCode(),
                                    String.join(", ", missingPermissions)));
        }
    }

    /**
     * 验证指定角色组类型节点的权限
     */
    private void validateSpecifiedRoleGroupPermission(WorkflowNodeDTO node, List<String> errors) {
        Long roleGroupId = node.getAssigneeRoleGroupId();
        if (roleGroupId == null) {
            errors.add(String.format("• 节点 [%s] 使用 SPECIFIED_ROLE_GROUP 但未配置 assigneeRoleGroupId",
                                    node.getNodeName()));
            return;
        }

        // 查询角色组下的所有角色
        List<Role> roles = roleMapper.selectList(
            new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleGroupId, roleGroupId)
                .eq(Role::getDeletedAt, 0)
        );

        if (roles.isEmpty()) {
            errors.add(String.format("• 节点 [%s] 配置的角色组ID [%d] 下没有有效角色",
                                    node.getNodeName(), roleGroupId));
            return;
        }

        // 检查角色组下的每个角色
        for (Role role : roles) {
            List<String> missingPermissions = checkRoleMissingPermissions(role.getId());
            if (!missingPermissions.isEmpty()) {
                errors.add(String.format("• 节点 [%s] 配置的角色组中，角色 [%s(%s)] 缺少权限: %s",
                                        node.getNodeName(),
                                        role.getName(),
                                        role.getCode(),
                                        String.join(", ", missingPermissions)));
            }
        }
    }

    /**
     * 检查角色缺少哪些必要权限
     *
     * @param roleId 角色ID
     * @return 缺少的权限代码列表
     */
    private List<String> checkRoleMissingPermissions(Long roleId) {
        // 查询角色已有的权限
        List<Long> existingPermissionIds = rolePermissionMapper.selectList(
            new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, roleId)
        ).stream()
         .map(SysRolePermission::getPermissionId)
         .collect(Collectors.toList());

        // 查询必要的权限
        List<SysPermission> requiredPermissions = permissionMapper.selectList(
            new LambdaQueryWrapper<SysPermission>()
                .in(SysPermission::getCode, REQUIRED_PERMISSION_CODES)
        );

        // 找出缺少的权限
        return requiredPermissions.stream()
            .filter(permission -> !existingPermissionIds.contains(permission.getId()))
            .map(permission -> String.format("%s(%s)", permission.getName(), permission.getCode()))
            .collect(Collectors.toList());
    }

    /**
     * 自动修复角色缺少的权限（可选功能）
     * 仅在开发/测试环境使用，生产环境应通过管理界面手动配置
     *
     * @param roleId 角色ID
     * @return 添加的权限数量
     */
    public int autoFixRolePermissions(Long roleId) {
        List<String> missingPermissions = checkRoleMissingPermissions(roleId);
        if (missingPermissions.isEmpty()) {
            return 0;
        }

        log.warn("自动为角色 [{}] 添加缺少的权限: {}", roleId, missingPermissions);

        // 查询权限ID
        List<SysPermission> permissions = permissionMapper.selectList(
            new LambdaQueryWrapper<SysPermission>()
                .in(SysPermission::getCode, REQUIRED_PERMISSION_CODES)
        );

        int addedCount = 0;
        for (SysPermission permission : permissions) {
            // 检查是否已存在
            Long count = rolePermissionMapper.selectCount(
                new LambdaQueryWrapper<SysRolePermission>()
                    .eq(SysRolePermission::getRoleId, roleId)
                    .eq(SysRolePermission::getPermissionId, permission.getId())
            );

            if (count == 0) {
                SysRolePermission rp = new SysRolePermission();
                rp.setRoleId(roleId);
                rp.setPermissionId(permission.getId());
                rolePermissionMapper.insert(rp);
                addedCount++;
            }
        }

        return addedCount;
    }
}
