package com.demand.system.module.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.module.notification.service.NotificationService;
import com.demand.system.module.organization.entity.SysOrg;
import com.demand.system.module.organization.mapper.SysOrgMapper;
import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.requirement.entity.RequirementPendingTask;
import com.demand.system.module.requirement.mapper.RequirementPendingTaskMapper;
import com.demand.system.module.rbac.entity.Role;
import com.demand.system.module.rbac.entity.UserRole;
import com.demand.system.module.rbac.mapper.RoleMapper;
import com.demand.system.module.rbac.mapper.UserRoleMapper;
import com.demand.system.module.user.entity.User;
import com.demand.system.module.user.entity.UserOrganization;
import com.demand.system.module.user.mapper.UserMapper;
import com.demand.system.module.user.mapper.UserOrganizationMapper;
import com.demand.system.module.workflow.engine.WorkflowGraphContext;
import com.demand.system.module.workflow.engine.WorkflowGraphNavigator;
import com.demand.system.module.workflow.entity.WorkflowInstance;
import com.demand.system.module.workflow.entity.WorkflowNode;
import com.demand.system.module.workflow.support.WorkflowNodeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工作流抄送处理。抄送节点是透传节点，不会成为实例当前等待节点，
 * 因此必须在“当前等待节点 -> 下一个等待节点”的实际路径上显式执行。
 */
@Service
public class WorkflowCcService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowCcService.class);
    public static final String PROP_CC_MODE = "ccMode";
    public static final String MODE_MESSAGE = "MESSAGE";
    public static final String MODE_READ_ONLY_TODO = "READ_ONLY_TODO";
    public static final String TASK_TYPE_CC_READ_ONLY = "CC_READ_ONLY";

    private final WorkflowGraphNavigator graphNavigator;
    private final NotificationService notificationService;
    private final RequirementPendingTaskMapper pendingTaskMapper;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final UserOrganizationMapper userOrganizationMapper;
    private final SysOrgMapper sysOrgMapper;

    public WorkflowCcService(WorkflowGraphNavigator graphNavigator,
                             NotificationService notificationService,
                             RequirementPendingTaskMapper pendingTaskMapper,
                             UserMapper userMapper,
                             UserRoleMapper userRoleMapper,
                             RoleMapper roleMapper,
                             UserOrganizationMapper userOrganizationMapper,
                             SysOrgMapper sysOrgMapper) {
        this.graphNavigator = graphNavigator;
        this.notificationService = notificationService;
        this.pendingTaskMapper = pendingTaskMapper;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.userOrganizationMapper = userOrganizationMapper;
        this.sysOrgMapper = sysOrgMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void process(Requirement requirement, WorkflowInstance instance, WorkflowGraphContext context,
                        String fromWaitNodeId, String targetWaitNodeId, Long operatorId) {
        if (requirement == null || instance == null || context == null
                || !StringUtils.hasText(fromWaitNodeId) || !StringUtils.hasText(targetWaitNodeId)) {
            return;
        }
        List<String> path = graphNavigator.resolvePathToWaitNode(
                context, fromWaitNodeId, targetWaitNodeId, requirement);
        if (path.isEmpty()) {
            return;
        }

        List<RequirementPendingTask> readOnlyTasks = new ArrayList<>();
        Set<String> sentMessageKeys = new LinkedHashSet<>();
        for (String nodeId : path) {
            WorkflowNode node = context.getNode(nodeId);
            if (node == null || !"cc".equalsIgnoreCase(node.getNodeType())) {
                continue;
            }
            Set<Long> recipientIds = resolveRecipientIds(requirement, node, operatorId);
            if (recipientIds.isEmpty()) {
                log.warn("抄送节点未解析到收件人: requirementId={}, nodeId={}", requirement.getId(), node.getNodeId());
                continue;
            }
            String mode = resolveMode(node);
            if (MODE_READ_ONLY_TODO.equals(mode)) {
                for (Long recipientId : recipientIds) {
                    RequirementPendingTask task = new RequirementPendingTask(
                            requirement.getId(), recipientId, node.getAssigneeType(),
                            instance.getId(), node.getNodeId(), node.getNodeName());
                    task.setTaskType(TASK_TYPE_CC_READ_ONLY);
                    readOnlyTasks.add(task);
                }
            } else {
                String title = "需求抄送通知";
                String content = String.format("需求【%s】已抄送至您，请查阅节点【%s】",
                        requirement.getTitle(), node.getNodeName());
                for (Long recipientId : recipientIds) {
                    String key = recipientId + "@" + node.getNodeId();
                    if (sentMessageKeys.add(key)) {
                        notificationService.sendNotification(recipientId, title, content,
                                "requirement_cc", requirement.getId());
                    }
                }
            }
        }
        if (!readOnlyTasks.isEmpty()) {
            pendingTaskMapper.insertCcReadOnlyBatch(readOnlyTasks);
        }
    }

    private String resolveMode(WorkflowNode node) {
        Object value = WorkflowNodeUtils.readProperty(node, PROP_CC_MODE);
        if (value == null || !StringUtils.hasText(value.toString())) {
            return MODE_MESSAGE;
        }
        String mode = value.toString().trim().toUpperCase();
        return MODE_READ_ONLY_TODO.equals(mode) ? MODE_READ_ONLY_TODO : MODE_MESSAGE;
    }

    private Set<Long> resolveRecipientIds(Requirement requirement, WorkflowNode node, Long operatorId) {
        Set<Long> result = new LinkedHashSet<>();
        String assigneeType = node.getAssigneeType();
        if (!StringUtils.hasText(assigneeType)) {
            return result;
        }
        switch (assigneeType) {
            case "SPECIFIED_USER" -> addAll(result, node.getAssigneeUserIds());
            case "SPECIFIED_ROLE" -> {
                if (node.getAssigneeRoleId() != null) {
                    addAll(result, userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                            .eq(UserRole::getRoleId, node.getAssigneeRoleId().longValue()))
                            .stream().map(UserRole::getUserId).toList());
                }
            }
            case "SPECIFIED_ROLE_GROUP" -> {
                if (node.getAssigneeRoleGroupId() != null) {
                    Set<Long> roleIds = roleMapper.selectList(new LambdaQueryWrapper<Role>()
                                    .eq(Role::getRoleGroupId, node.getAssigneeRoleGroupId()))
                            .stream().map(Role::getId).filter(Objects::nonNull).collect(Collectors.toSet());
                    if (!roleIds.isEmpty()) {
                        addAll(result, userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                                        .in(UserRole::getRoleId, roleIds))
                                .stream().map(UserRole::getUserId).toList());
                    }
                }
            }
            case "SPECIFIED_ORG" -> addAll(result, resolveOrgUserIds(node));
            case "CREATOR" -> addOne(result, requirement.getCreatorId());
            case "PREV_APPROVER" -> addOne(result, operatorId);
            default -> log.warn("未知的抄送人类型: {}", assigneeType);
        }
        result.remove(null);
        return result;
    }

    private Set<Long> resolveOrgUserIds(WorkflowNode node) {
        Long orgId = node.getAssigneeOrgId();
        if (orgId == null) {
            return Set.of();
        }
        Set<Long> orgIds = new LinkedHashSet<>();
        orgIds.add(orgId);
        Object scope = WorkflowNodeUtils.readProperty(node, "orgScopeType");
        if (!"current".equalsIgnoreCase(String.valueOf(scope))) {
            List<SysOrg> children = sysOrgMapper.selectList(new LambdaQueryWrapper<SysOrg>()
                    .like(SysOrg::getPath, "/" + orgId + "/"));
            children.stream().map(SysOrg::getId).filter(Objects::nonNull).forEach(orgIds::add);
        }
        Set<Long> userIds = new LinkedHashSet<>();
        if (!orgIds.isEmpty()) {
            addAll(userIds, userMapper.selectList(new LambdaQueryWrapper<User>()
                    .and(w -> w.in(User::getOrgId, orgIds)
                            .or().in(User::getDepartmentId, orgIds)
                            .or().in(User::getRegionId, orgIds)))
                    .stream().map(User::getId).toList());
            addAll(userIds, userOrganizationMapper.selectList(new LambdaQueryWrapper<UserOrganization>()
                    .and(w -> w.in(UserOrganization::getOrgId, orgIds)
                            .or().in(UserOrganization::getDepartmentId, orgIds)
                            .or().in(UserOrganization::getRegionId, orgIds)))
                    .stream().map(UserOrganization::getUserId).toList());
        }
        return userIds;
    }

    private void addOne(Set<Long> target, Long value) {
        if (value != null) target.add(value);
    }

    private void addAll(Set<Long> target, Collection<Long> values) {
        if (values != null) values.stream().filter(Objects::nonNull).forEach(target::add);
    }
}
