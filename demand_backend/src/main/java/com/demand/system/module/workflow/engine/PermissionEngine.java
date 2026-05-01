package com.demand.system.module.workflow.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.requirement.mapper.RequirementMapper;
import com.demand.system.module.user.entity.UserOrganization;
import com.demand.system.module.user.mapper.UserOrganizationMapper;
import com.demand.system.module.workflow.entity.WorkflowNodePermission;
import com.demand.system.module.workflow.entity.WorkflowState;
import com.demand.system.module.workflow.entity.WorkflowTransition;
import com.demand.system.module.workflow.entity.WorkflowVersion;
import com.demand.system.module.workflow.mapper.WorkflowNodePermissionMapper;
import com.demand.system.module.workflow.mapper.WorkflowStateMapper;
import com.demand.system.module.workflow.mapper.WorkflowTransitionMapper;
import com.demand.system.module.workflow.mapper.WorkflowVersionMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionEngine {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<Long>> LONG_LIST = new TypeReference<>() {
    };

    private final WorkflowTransitionMapper transitionMapper;
    private final UserOrganizationMapper userOrganizationMapper;
    private final WorkflowVersionMapper workflowVersionMapper;
    private final WorkflowNodePermissionMapper nodePermissionMapper;
    private final WorkflowStateMapper stateMapper;
    private final RequirementMapper requirementMapper;
    private final WorkflowDefinitionEngine workflowDefinitionEngine;
    private final ObjectMapper objectMapper;

    public boolean canTransition(Long requirementId, Long fromStateId, Long toStateId, Long userId) {
        Requirement requirement = requirementMapper.selectById(requirementId);
        if (requirement == null) {
            log.warn("Requirement {} not found while checking transition permission", requirementId);
            return false;
        }

        WorkflowState fromState = stateMapper.selectById(fromStateId);
        WorkflowState targetState = stateMapper.selectById(toStateId);
        if (fromState == null || targetState == null) {
            log.warn("Runtime state not found while checking transition permission: {} -> {}", fromStateId, toStateId);
            return false;
        }

        WorkflowTransition transition = transitionMapper.selectOne(new LambdaQueryWrapper<WorkflowTransition>()
                .eq(WorkflowTransition::getFromStateId, fromStateId)
                .eq(WorkflowTransition::getToStateId, toStateId)
                .last("LIMIT 1"));

        boolean definitionDriven = workflowDefinitionEngine.hasActiveDefinition(requirement.getProjectId());
        Optional<WorkflowDefinitionEngine.ResolvedTransitionSpec> resolvedSpec = definitionDriven
                ? workflowDefinitionEngine.resolveTransition(requirement, fromState.getName(), targetState.getName())
                : Optional.empty();

        if (definitionDriven && resolvedSpec.isEmpty()) {
            log.warn("No executable BPMN path from {} to {} for requirement {}", fromState.getName(), targetState.getName(), requirementId);
            return false;
        }

        if (!definitionDriven && transition == null) {
            log.warn("No transition configured from state {} to state {}", fromStateId, toStateId);
            return false;
        }

        Long projectId = requirement.getProjectId();
        if (!checkNodePermission(projectId, fromStateId, requirement, userId)) {
            log.warn("User {} not permitted by node permission on state {}", userId, fromStateId);
            return false;
        }

        String allowedRolesPayload = resolvedSpec
                .map(WorkflowDefinitionEngine.ResolvedTransitionSpec::allowedRolesJson)
                .orElseGet(() -> transition == null ? null : transition.getAllowedRoles());
        Set<String> allowedRoleSet = parseStringCollection(allowedRolesPayload);
        if (allowedRoleSet.isEmpty()) {
            return true;
        }

        if (!matchesRolePermission(allowedRoleSet, requirement, userId)) {
            log.warn("User {} does not have any of the required roles {} for transition {}",
                    userId, allowedRoleSet, transition.getId());
            return false;
        }
        return true;
    }

    private boolean checkNodePermission(Long projectId, Long fromStateId, Requirement requirement, Long userId) {
        if (projectId == null || fromStateId == null || userId == null) {
            return true;
        }

        WorkflowVersion activeVersion = getActiveVersion(projectId).orElse(null);
        if (activeVersion == null) {
            return true;
        }

        WorkflowState runtimeState = stateMapper.selectById(fromStateId);
        if (runtimeState == null || !StringUtils.hasText(runtimeState.getName())) {
            return true;
        }

        LambdaQueryWrapper<WorkflowNodePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkflowNodePermission::getWorkflowVersionId, activeVersion.getId())
                .eq(WorkflowNodePermission::getNodeId, runtimeState.getName().trim())
                .last("LIMIT 1");
        WorkflowNodePermission permission = nodePermissionMapper.selectOne(wrapper);
        if (permission == null) {
            return true;
        }

        Set<Long> allowedUsers = parseLongCollection(permission.getAllowedUsers());
        if (!allowedUsers.isEmpty() && allowedUsers.contains(userId)) {
            return true;
        }

        Set<String> allowedRoles = parseStringCollection(permission.getAllowedRoles());
        if (!allowedRoles.isEmpty()) {
            return matchesRolePermission(allowedRoles, requirement, userId);
        }

        return allowedUsers.isEmpty();
    }

    private boolean matchesRolePermission(Set<String> allowedRoles, Requirement requirement, Long userId) {
        if (allowedRoles.isEmpty()) {
            return true;
        }

        if (requirement != null) {
            if (allowedRoles.contains("创建人") && userId.equals(requirement.getCreatorId())) {
                return true;
            }
            if ((allowedRoles.contains("负责人") || allowedRoles.contains("处理人"))
                    && userId.equals(requirement.getAssigneeId())) {
                return true;
            }
        }

        Set<String> userRoles = getUserRoles(userId);
        return userRoles.stream().anyMatch(allowedRoles::contains);
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
                .filter(StringUtils::hasText)
                .flatMap(raw -> Arrays.stream(raw.split(",")))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> parseStringCollection(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Set.of();
        }
        try {
            List<String> values = objectMapper.readValue(raw, STRING_LIST);
            return values.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } catch (Exception ignore) {
            return Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .map(this::stripJsonNoise)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }

    private Set<Long> parseLongCollection(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Set.of();
        }
        try {
            List<Long> values = objectMapper.readValue(raw, LONG_LIST);
            return values.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } catch (Exception ignore) {
            return Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .map(this::stripJsonNoise)
                    .filter(StringUtils::hasText)
                    .map(value -> {
                        try {
                            return Long.parseLong(value);
                        } catch (NumberFormatException e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }

    private String stripJsonNoise(String value) {
        return value.replace("[", "")
                .replace("]", "")
                .replace("\"", "")
                .trim();
    }
}
