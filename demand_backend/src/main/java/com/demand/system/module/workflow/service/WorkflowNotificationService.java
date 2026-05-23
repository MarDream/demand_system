package com.demand.system.module.workflow.service;

import com.demand.system.module.notification.service.NotificationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.module.rbac.entity.UserRole;
import com.demand.system.module.rbac.mapper.UserRoleMapper;
import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.workflow.entity.WorkflowNode;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class WorkflowNotificationService {

    private final NotificationService notificationService;
    private final UserRoleMapper userRoleMapper;

    public WorkflowNotificationService(NotificationService notificationService, UserRoleMapper userRoleMapper) {
        this.notificationService = notificationService;
        this.userRoleMapper = userRoleMapper;
    }

    public void notifyNodeEntered(Requirement requirement, WorkflowNode node, Long operatorId) {
        if (requirement == null || node == null) {
            return;
        }
        Set<Long> recipientIds = resolveRecipientIds(node, operatorId);
        if (recipientIds.isEmpty()) {
            return;
        }

        String title = "需求待处理";
        String content = String.format("需求【%s】已进入节点【%s】，请及时处理", requirement.getTitle(), node.getNodeName());
        for (Long recipientId : recipientIds) {
            notificationService.sendNotification(recipientId, title, content, "requirement", requirement.getId());
        }
    }

    private Set<Long> resolveRecipientIds(WorkflowNode node, Long operatorId) {
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
        }
        recipientIds.remove(operatorId);
        return recipientIds;
    }
}
