package com.demand.system.module.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.module.notification.service.NotificationService;
import com.demand.system.module.rbac.entity.UserRole;
import com.demand.system.module.rbac.mapper.UserRoleMapper;
import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.workflow.entity.WorkflowInstanceTransition;
import com.demand.system.module.workflow.entity.WorkflowNode;
import com.demand.system.module.workflow.mapper.WorkflowInstanceTransitionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class WorkflowNotificationService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowNotificationService.class);

    /** 节点属性：是否启用消息提醒开关 */
    public static final String PROP_NOTIFY_ON_ENTER = "notifyOnEnter";
    /** 节点属性：消息提醒范围 — 沿已审批节点路径上审批过需求的所有用户（含创建人） */
    public static final String NOTIFY_SCOPE_PATH_APPROVERS = "PATH_APPROVERS";
    /** 节点属性：消息提醒范围 — 从需求创建节点到当前节点实际处理过的所有用户 */
    public static final String NOTIFY_SCOPE_ACTUAL_HANDLERS = "ACTUAL_HANDLERS";

    private final NotificationService notificationService;
    private final UserRoleMapper userRoleMapper;
    private final WorkflowInstanceTransitionMapper transitionMapper;

    public WorkflowNotificationService(NotificationService notificationService,
                                      UserRoleMapper userRoleMapper,
                                      WorkflowInstanceTransitionMapper transitionMapper) {
        this.notificationService = notificationService;
        this.userRoleMapper = userRoleMapper;
        this.transitionMapper = transitionMapper;
    }

    /**
     * 进入新节点时，向该节点的处理人发送站内消息（原有逻辑，保持不变）。
     */
    public void notifyNodeEntered(Requirement requirement, WorkflowNode node, Long operatorId) {
        if (requirement == null || node == null) {
            return;
        }
        Set<Long> recipientIds = resolveRecipientIds(requirement, node, operatorId);
        if (recipientIds.isEmpty()) {
            return;
        }

        String title = "需求待处理";
        String content = String.format("需求【%s】已进入节点【%s】，请及时处理", requirement.getTitle(), node.getNodeName());
        for (Long recipientId : recipientIds) {
            notificationService.sendNotification(recipientId, title, content, "requirement", requirement.getId());
        }
    }

    /**
     * 流转后通知：若目标节点开启了消息提醒开关，按所选范围向已审批路径 / 实际处理用户推送站内消息。
     *
     * @param requirement       当前需求
     * @param enteredNode       刚刚进入的目标节点
     * @param operatorId        当前操作人（执行本次流转的用户）
     * @param instanceId        工作流实例 ID，用于查询流转历史
     */
    public void notifyApproversOnTransition(Requirement requirement,
                                           WorkflowNode enteredNode,
                                           Long operatorId,
                                           Long instanceId) {
        if (requirement == null || enteredNode == null || instanceId == null) {
            return;
        }
        if (!isNotifyOnEnterEnabled(enteredNode)) {
            return;
        }
        String scope = resolveNotifyScope(enteredNode);
        Set<Long> recipientIds;
        try {
            recipientIds = switch (scope) {
                case NOTIFY_SCOPE_ACTUAL_HANDLERS -> resolveActualHandlerIds(instanceId, requirement);
                default -> resolvePathApproverIds(instanceId, requirement);
            };
        } catch (Exception ex) {
            log.warn("计算流转通知接收人失败, instanceId={}, scope={}", instanceId, scope, ex);
            return;
        }

        recipientIds.remove(operatorId);

        if (recipientIds.isEmpty()) {
            log.debug("通知开关已开启但未解析到接收人, nodeId={}, scope={}", enteredNode.getNodeId(), scope);
            return;
        }

        String title = "需求流转通知";
        String content = String.format("需求【%s】已流转至节点【%s】，请知悉", requirement.getTitle(), enteredNode.getNodeName());
        for (Long recipientId : recipientIds) {
            notificationService.sendNotification(recipientId, title, content, "requirement", requirement.getId());
        }
    }

    private Set<Long> resolveRecipientIds(Requirement requirement, WorkflowNode node, Long operatorId) {
        Set<Long> recipientIds = new LinkedHashSet<>();
        String assigneeType = node.getAssigneeType();
        if ("SPECIFIED_USER".equals(assigneeType) && node.getAssigneeUserIds() != null) {
            recipientIds.addAll(node.getAssigneeUserIds());
        } else if ("SPECIFIED_ROLE".equals(assigneeType) && node.getAssigneeRoleId() != null) {
            List<UserRole> userRoles = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                    .eq(UserRole::getRoleId, node.getAssigneeRoleId().longValue()));
            for (UserRole userRole : userRoles) {
                if (userRole.getUserId() != null) {
                    recipientIds.add(userRole.getUserId());
                }
            }
        } else if ("CREATOR".equals(assigneeType) && requirement.getCreatorId() != null) {
            recipientIds.add(requirement.getCreatorId());
        }
        recipientIds.remove(operatorId);
        return recipientIds;
    }

    /**
     * 解析"已审批节点路径"上的用户集合：
     * 包括需求创建人 + 当前工作流实例所有 transition 的操作人（按时间倒序遍历整条审批路径）。
     */
    private Set<Long> resolvePathApproverIds(Long instanceId, Requirement requirement) {
        Set<Long> recipientIds = new LinkedHashSet<>();
        if (requirement != null && requirement.getCreatorId() != null) {
            recipientIds.add(requirement.getCreatorId());
        }
        List<WorkflowInstanceTransition> transitions = transitionMapper.selectList(
                new LambdaQueryWrapper<WorkflowInstanceTransition>()
                        .eq(WorkflowInstanceTransition::getInstanceId, instanceId)
                        .orderByAsc(WorkflowInstanceTransition::getId)
        );
        for (WorkflowInstanceTransition transition : transitions) {
            if (transition == null) {
                continue;
            }
            Long operator = transition.getOperatorId();
            if (operator != null) {
                recipientIds.add(operator);
            }
        }
        recipientIds.remove(null);
        return recipientIds;
    }

    /**
     * 解析"实际处理用户"集合：
     * 包括需求创建人 + 已流转过的所有 transition 操作人（去重），即从开始节点到当前节点过程中真实处理过的用户。
     * 与 {@link #resolvePathApproverIds} 区别：去重合并创建人与操作人，作为同一份接收人集合。
     */
    private Set<Long> resolveActualHandlerIds(Long instanceId, Requirement requirement) {
        // 实际处理用户 = 已审批路径上的操作人 ∪ 需求创建人，实现上与 PATH_APPROVERS 等价。
        // 保留为独立方法，便于后续扩展（例如仅通知"实际操作过的非创建人用户"）。
        return resolvePathApproverIds(instanceId, requirement);
    }

    private boolean isNotifyOnEnterEnabled(WorkflowNode node) {
        Map<String, Object> properties = node.getProperties();
        if (properties == null) {
            return false;
        }
        Object value = properties.get(PROP_NOTIFY_ON_ENTER);
        return Boolean.TRUE.equals(value);
    }

    private String resolveNotifyScope(WorkflowNode node) {
        Map<String, Object> properties = node.getProperties();
        if (properties == null) {
            return NOTIFY_SCOPE_PATH_APPROVERS;
        }
        Object value = properties.get("notifyScope");
        if (value instanceof String scope && StringUtils.hasText(scope)) {
            String normalized = scope.trim().toUpperCase();
            if (NOTIFY_SCOPE_ACTUAL_HANDLERS.equals(normalized) || NOTIFY_SCOPE_PATH_APPROVERS.equals(normalized)) {
                return normalized;
            }
        }
        return NOTIFY_SCOPE_PATH_APPROVERS;
    }
}