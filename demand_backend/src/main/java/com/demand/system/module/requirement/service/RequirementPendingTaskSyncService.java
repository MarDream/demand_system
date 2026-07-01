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
import java.util.List;

/**
 * 需求待办任务同步服务
 * 负责在需求流转时维护待办任务物化表
 *
 * 存储策略：
 * - 有 selectedAssigneeId（选中具体用户）：存 user_id
 * - SPECIFIED_ROLE：无选中人时，存 role_id
 * - SPECIFIED_ROLE_GROUP：无选中人时，存 role_group_id
 * - SPECIFIED_ORG：无选中人时，存 org_id
 * - SPECIFIED_USER（无选中人）：存所有候选人 user_id
 * - CREATOR/PREV_APPROVER：存 user_id
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
     *
     * @param requirementId 需求ID
     * @param selectedAssigneeId 本次流转选择的处理人ID（可为null）
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncPendingTasks(Long requirementId, Long selectedAssigneeId) {
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
            List<RequirementPendingTask> tasks = buildPendingTasks(requirement, instance, currentNode, selectedAssigneeId);

            // 5. 批量插入新待办记录
            if (!tasks.isEmpty()) {
                pendingTaskMapper.insertBatch(tasks);
                log.info("同步待办任务成功: requirement={}, taskCount={}, assigneeType={}, selectedAssigneeId={}",
                        requirementId, tasks.size(), currentNode.getAssigneeType(), selectedAssigneeId);
            }
        } catch (Exception e) {
            log.error("同步待办任务失败: requirementId={}", requirementId, e);
            throw e;
        }
    }

    /**
     * 重载方法：兼容原有调用（无 selectedAssigneeId）
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncPendingTasks(Long requirementId) {
        syncPendingTasks(requirementId, null);
    }

    /**
     * 构建待办任务列表
     */
    private List<RequirementPendingTask> buildPendingTasks(Requirement requirement, WorkflowInstance instance,
                                                          WorkflowNode node, Long selectedAssigneeId) {
        List<RequirementPendingTask> tasks = new ArrayList<>();
        String assigneeType = node.getAssigneeType();

        // 如果有选中的具体用户，直接存该用户
        if (selectedAssigneeId != null) {
            RequirementPendingTask task = new RequirementPendingTask(
                requirement.getId(),
                "SPECIFIED_USER",
                selectedAssigneeId, null, null,
                instance.getId(),
                node.getNodeId(),
                node.getNodeName()
            );
            tasks.add(task);
            return tasks;
        }

        // 无选中人时，根据类型存储对应字段
        switch (assigneeType) {
            case "SPECIFIED_USER":
                // 指定用户：存所有候选人 user_id
                if (node.getAssigneeUserIds() != null && !node.getAssigneeUserIds().isEmpty()) {
                    for (Long userId : node.getAssigneeUserIds()) {
                        tasks.add(new RequirementPendingTask(
                            requirement.getId(),
                            "SPECIFIED_USER",
                            userId, null, null,
                            instance.getId(),
                            node.getNodeId(),
                            node.getNodeName()
                        ));
                    }
                }
                break;

            case "SPECIFIED_ROLE":
                // 指定角色：存 role_id
                if (node.getAssigneeRoleId() != null) {
                    tasks.add(new RequirementPendingTask(
                        requirement.getId(),
                        "SPECIFIED_ROLE",
                        null, node.getAssigneeRoleId().longValue(), null,
                        instance.getId(),
                        node.getNodeId(),
                        node.getNodeName()
                    ));
                }
                break;

            case "SPECIFIED_ROLE_GROUP":
                // 指定角色组：存 role_group_id
                if (node.getAssigneeRoleGroupId() != null) {
                    tasks.add(new RequirementPendingTask(
                        requirement.getId(),
                        "SPECIFIED_ROLE_GROUP",
                        null, null, node.getAssigneeRoleGroupId(),
                        instance.getId(),
                        node.getNodeId(),
                        node.getNodeName()
                    ));
                }
                break;

            case "SPECIFIED_ORG":
                // 指定组织：存 org_id
                if (node.getAssigneeOrgId() != null) {
                    tasks.add(new RequirementPendingTask(
                        requirement.getId(),
                        "SPECIFIED_ORG",
                        null, null, node.getAssigneeOrgId(),
                        instance.getId(),
                        node.getNodeId(),
                        node.getNodeName()
                    ));
                }
                break;

            case "CREATOR":
                // 需求创建人：存 user_id
                if (requirement.getCreatorId() != null) {
                    tasks.add(new RequirementPendingTask(
                        requirement.getId(),
                        "CREATOR",
                        requirement.getCreatorId(), null, null,
                        instance.getId(),
                        node.getNodeId(),
                        node.getNodeName()
                    ));
                }
                break;

            case "PREV_APPROVER":
                // 上一个审批人：存 user_id
                WorkflowInstanceTransition prevTransition = transitionMapper.selectOne(
                    new LambdaQueryWrapper<WorkflowInstanceTransition>()
                        .eq(WorkflowInstanceTransition::getInstanceId, instance.getId())
                        .eq(WorkflowInstanceTransition::getToNodeId, instance.getCurrentNodeId())
                        .orderByDesc(WorkflowInstanceTransition::getId)
                        .last("LIMIT 1")
                );
                if (prevTransition != null && prevTransition.getOperatorId() != null) {
                    tasks.add(new RequirementPendingTask(
                        requirement.getId(),
                        "PREV_APPROVER",
                        prevTransition.getOperatorId(), null, null,
                        instance.getId(),
                        node.getNodeId(),
                        node.getNodeName()
                    ));
                }
                break;

            default:
                log.warn("未知的待办人类型: {}", assigneeType);
        }

        return tasks;
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
