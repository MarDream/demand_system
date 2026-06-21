package com.demand.system.module.requirement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.requirement.entity.RequirementPendingTask;
import com.demand.system.module.requirement.mapper.RequirementMapper;
import com.demand.system.module.requirement.mapper.RequirementPendingTaskMapper;
import com.demand.system.module.rbac.entity.Role;
import com.demand.system.module.rbac.mapper.RoleMapper;
import com.demand.system.module.user.entity.User;
import com.demand.system.module.user.entity.UserOrganization;
import com.demand.system.module.user.mapper.UserMapper;
import com.demand.system.module.user.mapper.UserOrganizationMapper;
import com.demand.system.module.workflow.entity.WorkflowInstance;
import com.demand.system.module.workflow.entity.WorkflowInstanceTransition;
import com.demand.system.module.workflow.entity.WorkflowNode;
import com.demand.system.module.workflow.mapper.WorkflowInstanceMapper;
import com.demand.system.module.workflow.mapper.WorkflowInstanceTransitionMapper;
import com.demand.system.module.workflow.mapper.WorkflowNodeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 需求待办任务同步服务
 * 负责在需求流转时维护待办任务物化表
 */
@Service
public class RequirementPendingTaskSyncService {

    private static final Logger log = LoggerFactory.getLogger(RequirementPendingTaskSyncService.class);

    private final RequirementPendingTaskMapper pendingTaskMapper;
    private final RequirementMapper requirementMapper;
    private final WorkflowInstanceMapper workflowInstanceMapper;
    private final WorkflowNodeMapper workflowNodeMapper;
    private final WorkflowInstanceTransitionMapper transitionMapper;
    private final UserMapper userMapper;
    private final UserOrganizationMapper userOrganizationMapper;
    private final RoleMapper roleMapper;

    public RequirementPendingTaskSyncService(RequirementPendingTaskMapper pendingTaskMapper,
                                             RequirementMapper requirementMapper,
                                             WorkflowInstanceMapper workflowInstanceMapper,
                                             WorkflowNodeMapper workflowNodeMapper,
                                             WorkflowInstanceTransitionMapper transitionMapper,
                                             UserMapper userMapper,
                                             UserOrganizationMapper userOrganizationMapper,
                                             RoleMapper roleMapper) {
        this.pendingTaskMapper = pendingTaskMapper;
        this.requirementMapper = requirementMapper;
        this.workflowInstanceMapper = workflowInstanceMapper;
        this.workflowNodeMapper = workflowNodeMapper;
        this.transitionMapper = transitionMapper;
        this.userMapper = userMapper;
        this.userOrganizationMapper = userOrganizationMapper;
        this.roleMapper = roleMapper;
    }

    /**
     * 同步需求的待办任务
     * 在需求流转到新节点时调用
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncPendingTasks(Long requirementId) {
        try {
            // 1. 删除旧的待办记录
            pendingTaskMapper.deleteByRequirementId(requirementId);

            // 2. 查询需求和工作流信息
            Requirement requirement = requirementMapper.selectById(requirementId);
            if (requirement == null || requirement.getIsDraft()) {
                log.debug("需求不存在或为草稿，跳过待办同步: {}", requirementId);
                return;
            }

            WorkflowInstance instance = workflowInstanceMapper.selectOne(
                new LambdaQueryWrapper<WorkflowInstance>()
                    .eq(WorkflowInstance::getRequirementId, requirementId)
            );
            if (instance == null || !"running".equals(instance.getStatus())) {
                log.debug("工作流实例不存在或非运行状态，跳过待办同步: {}", requirementId);
                return;
            }

            // 3. 查询当前节点配置
            WorkflowNode currentNode = workflowNodeMapper.selectOne(
                new LambdaQueryWrapper<WorkflowNode>()
                    .eq(WorkflowNode::getWorkflowVersionId, instance.getWorkflowVersionId())
                    .eq(WorkflowNode::getNodeId, instance.getCurrentNodeId())
            );
            if (currentNode == null || currentNode.getAssigneeType() == null || currentNode.getAssigneeType().isEmpty()) {
                log.debug("当前节点无待办人配置，跳过待办同步: {}", requirementId);
                return;
            }

            // 4. 计算待办用户列表
            Set<Long> pendingUserIds = calculatePendingUsers(requirement, instance, currentNode);

            // 5. 批量插入新待办记录
            if (!pendingUserIds.isEmpty()) {
                List<RequirementPendingTask> tasks = new ArrayList<>();
                for (Long userId : pendingUserIds) {
                    tasks.add(new RequirementPendingTask(
                        requirementId,
                        userId,
                        currentNode.getAssigneeType(),
                        instance.getId(),
                        currentNode.getNodeId(),
                        currentNode.getNodeName()
                    ));
                }
                pendingTaskMapper.insertBatch(tasks);
                log.info("同步待办任务成功: requirement={}, users={}", requirementId, pendingUserIds.size());
            }
        } catch (Exception e) {
            log.error("同步待办任务失败: requirementId={}", requirementId, e);
            throw e;
        }
    }

    /**
     * 计算当前节点的待办用户列表
     */
    private Set<Long> calculatePendingUsers(Requirement requirement, WorkflowInstance instance, WorkflowNode node) {
        Set<Long> userIds = new HashSet<>();
        String assigneeType = node.getAssigneeType();

        switch (assigneeType) {
            case "SPECIFIED_USER":
                // 指定用户：从 JSON 数组中提取
                if (node.getAssigneeUserIds() != null) {
                    userIds.addAll(node.getAssigneeUserIds());
                }
                break;

            case "SPECIFIED_ROLE":
                // 指定角色：查询拥有该角色的用户
                if (node.getAssigneeRoleId() != null) {
                    List<User> users = userMapper.selectList(
                        new LambdaQueryWrapper<User>()
                            .inSql(User::getId, "SELECT user_id FROM role_user WHERE role_id = " + node.getAssigneeRoleId())
                    );
                    users.forEach(u -> userIds.add(u.getId()));
                }
                break;

            case "SPECIFIED_ROLE_GROUP":
                // 指定角色组：查询角色组内所有角色的用户
                if (node.getAssigneeRoleGroupId() != null) {
                    List<Role> roles = roleMapper.selectList(
                        new LambdaQueryWrapper<Role>()
                            .eq(Role::getRoleGroupId, node.getAssigneeRoleGroupId())
                            .eq(Role::getDeletedAt, 0)
                    );
                    for (Role role : roles) {
                        List<User> users = userMapper.selectList(
                            new LambdaQueryWrapper<User>()
                                .inSql(User::getId, "SELECT user_id FROM role_user WHERE role_id = " + role.getId())
                        );
                        users.forEach(u -> userIds.add(u.getId()));
                    }
                }
                break;

            case "SPECIFIED_ORG":
                // 指定组织：查询该组织的用户
                if (node.getAssigneeOrgId() != null) {
                    List<UserOrganization> userOrgs = userOrganizationMapper.selectList(
                        new LambdaQueryWrapper<UserOrganization>()
                            .eq(UserOrganization::getOrgId, node.getAssigneeOrgId())
                    );
                    userOrgs.forEach(uo -> userIds.add(uo.getUserId()));
                }
                break;

            case "CREATOR":
                // 需求创建人
                if (requirement.getCreatorId() != null) {
                    userIds.add(requirement.getCreatorId());
                }
                break;

            case "PREV_APPROVER":
                // 上一个审批人
                WorkflowInstanceTransition prevTransition = transitionMapper.selectOne(
                    new LambdaQueryWrapper<WorkflowInstanceTransition>()
                        .eq(WorkflowInstanceTransition::getInstanceId, instance.getId())
                        .eq(WorkflowInstanceTransition::getToNodeId, instance.getCurrentNodeId())
                        .orderByDesc(WorkflowInstanceTransition::getId)
                        .last("LIMIT 1")
                );
                if (prevTransition != null && prevTransition.getOperatorId() != null) {
                    userIds.add(prevTransition.getOperatorId());
                }
                break;

            default:
                log.warn("未知的待办人类型: {}", assigneeType);
        }

        return userIds;
    }

    /**
     * 批量同步多个需求的待办任务
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncPendingTasksBatch(List<Long> requirementIds) {
        for (Long requirementId : requirementIds) {
            try {
                syncPendingTasks(requirementId);
            } catch (Exception e) {
                log.error("批量同步待办任务失败: requirementId={}", requirementId, e);
                // 继续处理下一个，不中断整个批次
            }
        }
    }
}
