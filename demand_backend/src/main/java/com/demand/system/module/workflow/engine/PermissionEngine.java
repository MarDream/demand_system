package com.demand.system.module.workflow.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.module.user.entity.UserOrganization;
import com.demand.system.module.user.mapper.UserOrganizationMapper;
import com.demand.system.module.workflow.entity.WorkflowNodePermission;
import com.demand.system.module.workflow.entity.WorkflowTransition;
import com.demand.system.module.workflow.entity.WorkflowVersion;
import com.demand.system.module.workflow.mapper.WorkflowNodePermissionMapper;
import com.demand.system.module.workflow.mapper.WorkflowTransitionMapper;
import com.demand.system.module.workflow.mapper.WorkflowVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionEngine {

    private final WorkflowTransitionMapper transitionMapper;
    private final UserOrganizationMapper userOrganizationMapper;
    private final WorkflowVersionMapper workflowVersionMapper;
    private final WorkflowNodePermissionMapper nodePermissionMapper;

    /**
     * 判断用户是否有权执行状态转换
     *
     * @param requirementId 需求ID
     * @param fromStateId   起始状态ID
     * @param toStateId     目标状态ID
     * @param userId        用户ID
     * @return true 如果允许转换
     */
    public boolean canTransition(Long requirementId, Long fromStateId, Long toStateId, Long userId) {
        // 查询匹配的转换规则
        LambdaQueryWrapper<WorkflowTransition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkflowTransition::getFromStateId, fromStateId)
               .eq(WorkflowTransition::getToStateId, toStateId);
        WorkflowTransition transition = transitionMapper.selectOne(wrapper);

        if (transition == null) {
            log.warn("No transition configured from state {} to state {}", fromStateId, toStateId);
            return false;
        }

        Long projectId = transition.getProjectId();

        if (!checkNodePermission(projectId, fromStateId, userId)) {
            log.warn("User {} not permitted by node permission on state {}", userId, fromStateId);
            return false;
        }

        String allowedRoles = transition.getAllowedRoles();
        if (allowedRoles == null || allowedRoles.trim().isEmpty()) {
            return true;
        }

        Set<String> allowedRoleSet = parseCsv(allowedRoles);
        if (allowedRoleSet.isEmpty()) {
            return true;
        }

        Set<String> userRoleSet = getUserRoles(userId);
        boolean roleAllowed = userRoleSet.stream().anyMatch(allowedRoleSet::contains);
        if (!roleAllowed) {
            log.warn("User {} does not have any of the required roles {} for transition {}",
                    userId, allowedRoles, transition.getId());
        }
        return roleAllowed;
    }

    private boolean checkNodePermission(Long projectId, Long fromStateId, Long userId) {
        if (projectId == null || fromStateId == null || userId == null) return true;

        WorkflowVersion activeVersion = getActiveVersion(projectId).orElse(null);
        if (activeVersion == null) {
            return true;
        }

        LambdaQueryWrapper<WorkflowNodePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkflowNodePermission::getWorkflowVersionId, activeVersion.getId())
               .eq(WorkflowNodePermission::getNodeId, String.valueOf(fromStateId))
               .last("LIMIT 1");
        WorkflowNodePermission permission = nodePermissionMapper.selectOne(wrapper);
        if (permission == null) {
            return true;
        }

        Set<Long> allowedUsers = parseLongCsv(permission.getAllowedUsers());
        if (!allowedUsers.isEmpty() && allowedUsers.contains(userId)) {
            return true;
        }

        Set<String> allowedRoles = parseCsv(permission.getAllowedRoles());
        if (!allowedRoles.isEmpty()) {
            Set<String> userRoles = getUserRoles(userId);
            return userRoles.stream().anyMatch(allowedRoles::contains);
        }

        return allowedUsers.isEmpty();
    }

    private Optional<WorkflowVersion> getActiveVersion(Long projectId) {
        LambdaQueryWrapper<WorkflowVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkflowVersion::getProjectId, projectId)
               .eq(WorkflowVersion::getIsActive, 1)
               .orderByDesc(WorkflowVersion::getVersion)
               .last("LIMIT 1");
        return Optional.ofNullable(workflowVersionMapper.selectOne(wrapper));
    }

    private Set<String> getUserRoles(Long userId) {
        LambdaQueryWrapper<UserOrganization> userRoleWrapper = new LambdaQueryWrapper<>();
        userRoleWrapper.eq(UserOrganization::getUserId, userId);
        List<UserOrganization> userOrgs = userOrganizationMapper.selectList(userRoleWrapper);
        return userOrgs.stream()
                .map(UserOrganization::getSystemRole)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private Set<String> parseCsv(String csv) {
        if (csv == null) return Set.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private Set<Long> parseLongCsv(String csv) {
        if (csv == null) return Set.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try {
                        return Long.parseLong(s);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}
