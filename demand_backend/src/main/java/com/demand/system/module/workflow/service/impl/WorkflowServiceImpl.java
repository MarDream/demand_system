package com.demand.system.module.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.notification.service.NotificationService;
import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.requirement.entity.RequirementHistory;
import com.demand.system.module.requirement.mapper.RequirementHistoryMapper;
import com.demand.system.module.requirement.mapper.RequirementMapper;
import com.demand.system.module.workflow.dto.EdgeDTO;
import com.demand.system.module.workflow.dto.NodeConfigDTO;
import com.demand.system.module.workflow.dto.TransitionResponse;
import com.demand.system.module.workflow.dto.WorkflowDefinitionDTO;
import com.demand.system.module.workflow.engine.PermissionEngine;
import com.demand.system.module.workflow.engine.WorkflowVersionResolver;
import com.demand.system.module.workflow.engine.StateMachine;
import com.demand.system.module.workflow.engine.WorkflowDefinitionEngine;
import com.demand.system.module.workflow.entity.WorkflowNodePermission;
import com.demand.system.module.workflow.entity.WorkflowState;
import com.demand.system.module.workflow.entity.WorkflowTransition;
import com.demand.system.module.workflow.entity.WorkflowVersion;
import com.demand.system.module.workflow.entity.WorkflowNode;
import com.demand.system.module.workflow.mapper.WorkflowNodeMapper;
import com.demand.system.module.workflow.mapper.WorkflowNodePermissionMapper;
import com.demand.system.module.workflow.mapper.WorkflowStateMapper;
import com.demand.system.module.workflow.mapper.WorkflowTransitionMapper;
import com.demand.system.module.workflow.mapper.WorkflowVersionMapper;
import com.demand.system.module.workflow.service.WorkflowActivationService;
import com.demand.system.module.workflow.service.WorkflowService;
import com.demand.system.module.workflow.support.WorkflowVersionUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;


@Service
public class WorkflowServiceImpl implements WorkflowService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WorkflowServiceImpl.class);

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<Long>> LONG_LIST = new TypeReference<>() {
    };

    private final WorkflowStateMapper stateMapper;
    private final WorkflowTransitionMapper transitionMapper;
    private final WorkflowVersionMapper versionMapper;
    private final WorkflowNodeMapper workflowNodeMapper;
    private final WorkflowNodePermissionMapper nodePermissionMapper;
    private final WorkflowActivationService workflowActivationService;
    private final StateMachine stateMachine;
    private final PermissionEngine permissionEngine;
    private final WorkflowVersionResolver workflowVersionResolver;
    private final WorkflowDefinitionEngine workflowDefinitionEngine;
    private final RequirementMapper requirementMapper;
    private final RequirementHistoryMapper requirementHistoryMapper;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public WorkflowServiceImpl(WorkflowStateMapper stateMapper, WorkflowTransitionMapper transitionMapper,
                            WorkflowVersionMapper versionMapper, WorkflowNodeMapper workflowNodeMapper,
                            WorkflowNodePermissionMapper nodePermissionMapper,
                            WorkflowActivationService workflowActivationService,
                            StateMachine stateMachine, PermissionEngine permissionEngine,
                            WorkflowVersionResolver workflowVersionResolver, WorkflowDefinitionEngine workflowDefinitionEngine,
                            RequirementMapper requirementMapper, RequirementHistoryMapper requirementHistoryMapper,
                            NotificationService notificationService, ObjectMapper objectMapper) {
        this.stateMapper = stateMapper;
        this.transitionMapper = transitionMapper;
        this.versionMapper = versionMapper;
        this.workflowNodeMapper = workflowNodeMapper;
        this.nodePermissionMapper = nodePermissionMapper;
        this.workflowActivationService = workflowActivationService;
        this.stateMachine = stateMachine;
        this.permissionEngine = permissionEngine;
        this.workflowVersionResolver = workflowVersionResolver;
        this.workflowDefinitionEngine = workflowDefinitionEngine;
        this.requirementMapper = requirementMapper;
        this.requirementHistoryMapper = requirementHistoryMapper;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<WorkflowState> getStates(Long projectId) {
        Long runtimeProjectId = workflowVersionResolver.resolveRuntimeProjectId(projectId);
        return stateMapper.selectList(new LambdaQueryWrapper<WorkflowState>()
                .eq(WorkflowState::getProjectId, runtimeProjectId)
                .orderByAsc(WorkflowState::getSortOrder)
                .orderByAsc(WorkflowState::getId));
    }

    @Override
    public WorkflowState createState(Long projectId, WorkflowState state) {
        throw new BusinessException("运行态状态不支持直接维护，请通过工作流版本配置并启用后生效");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateState(Long id, WorkflowState state) {
        throw new BusinessException("运行态状态不支持直接维护，请通过工作流版本配置并启用后生效");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteState(Long id) {
        throw new BusinessException("运行态状态不支持直接维护，请通过工作流版本配置并启用后生效");
    }

    @Override
    public List<WorkflowTransition> getTransitions(Long projectId) {
        Long runtimeProjectId = workflowVersionResolver.resolveRuntimeProjectId(projectId);
        return transitionMapper.selectList(new LambdaQueryWrapper<WorkflowTransition>()
                .eq(WorkflowTransition::getProjectId, runtimeProjectId)
                .orderByAsc(WorkflowTransition::getFromStateId)
                .orderByAsc(WorkflowTransition::getToStateId)
                .orderByAsc(WorkflowTransition::getId));
    }

    @Override
    public WorkflowTransition createTransition(Long projectId, WorkflowTransition transition) {
        throw new BusinessException("运行态流转不支持直接维护，请通过工作流版本配置并启用后生效");
    }

    @Override
    public void updateTransition(Long id, WorkflowTransition transition) {
        throw new BusinessException("运行态流转不支持直接维护，请通过工作流版本配置并启用后生效");
    }

    @Override
    public void deleteTransition(Long id) {
        throw new BusinessException("运行态流转不支持直接维护，请通过工作流版本配置并启用后生效");
    }

    @Override
    public List<WorkflowTransition> getAvailableTransitions(Long requirementId, Long userId) {
        Requirement requirement = requirementMapper.selectById(requirementId);
        if (requirement == null || !StringUtils.hasText(requirement.getStatus())) {
            log.warn("Requirement {} not found or has no status", requirementId);
            return Collections.emptyList();
        }

        WorkflowState currentState = findStateByName(requirement.getProjectId(), requirement.getStatus());
        if (currentState == null) {
            log.warn("No workflow state found for status '{}' in project {}", requirement.getStatus(), requirement.getProjectId());
            return Collections.emptyList();
        }

        if (workflowDefinitionEngine.hasActiveDefinition(requirement.getProjectId())) {
            return workflowDefinitionEngine.resolveAvailableTransitions(requirement).stream()
                    .map(spec -> toRuntimeTransition(requirement.getProjectId(), currentState, spec))
                    .filter(Objects::nonNull)
                    .filter(t -> permissionEngine.canTransition(requirementId, currentState.getId(), t.getToStateId(), userId))
                    .toList();
        }

        List<WorkflowTransition> allTransitions = getTransitions(requirement.getProjectId()).stream()
                .filter(transition -> Objects.equals(transition.getFromStateId(), currentState.getId()))
                .sorted(Comparator.comparing(WorkflowTransition::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();

        return allTransitions.stream()
                .filter(t -> permissionEngine.canTransition(requirementId, currentState.getId(), t.getToStateId(), userId))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransitionResponse executeTransition(Long requirementId, Long targetStateId, Long userId, String comment) {
        Requirement requirement = requirementMapper.selectById(requirementId);
        if (requirement == null) {
            return TransitionResponse.builder().success(false).build();
        }

        WorkflowState currentState = findStateByName(requirement.getProjectId(), requirement.getStatus());
        if (currentState == null) {
            return TransitionResponse.builder().success(false).build();
        }

        String oldStatus = requirement.getStatus();
        boolean success = stateMachine.transition(requirementId, currentState.getId(), targetStateId, userId, comment);
        if (!success) {
            return TransitionResponse.builder().success(false).build();
        }

        WorkflowState targetState = stateMapper.selectById(targetStateId);
        String newStatus = targetState != null ? targetState.getName() : null;
        if (StringUtils.hasText(newStatus)) {
            recordStatusHistory(requirementId, userId, oldStatus, newStatus);
            sendStatusChangeNotifications(requirement, newStatus, userId);
        }

        return TransitionResponse.builder()
                .success(true)
                .newStatus(newStatus)
                .availableTransitions(getAvailableTransitions(requirementId, userId))
                .build();
    }

    @Override
    public List<WorkflowVersion> getVersions(Long projectId) {
        return versionMapper.selectList(new LambdaQueryWrapper<WorkflowVersion>()
                        .eq(WorkflowVersion::getProjectId, projectId))
                .stream()
                .sorted(WorkflowVersionUtils.byVersionDesc())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createVersion(WorkflowVersion version) {
        validateDefinitionOrThrow(version.getDefinition());

        WorkflowVersion latest = versionMapper.selectList(new LambdaQueryWrapper<WorkflowVersion>()
                        .eq(WorkflowVersion::getProjectId, version.getProjectId()))
                .stream()
                .sorted(WorkflowVersionUtils.byVersionDesc())
                .findFirst()
                .orElse(null);

        String requestedVersion = WorkflowVersionUtils.normalize(version.getVersion());
        if (requestedVersion != null) {
            if (!WorkflowVersionUtils.isValid(requestedVersion)) {
                throw new BusinessException("版本号格式需为正整数或 1.0.0");
            }
            boolean duplicateVersion = versionMapper.selectList(new LambdaQueryWrapper<WorkflowVersion>()
                            .eq(WorkflowVersion::getProjectId, version.getProjectId()))
                    .stream()
                    .anyMatch(item -> WorkflowVersionUtils.sameVersion(item.getVersion(), requestedVersion));
            if (duplicateVersion) {
                throw new BusinessException("版本号 V" + requestedVersion + " 已存在，请重新输入");
            }
            version.setVersion(requestedVersion);
        } else {
            version.setVersion(WorkflowVersionUtils.suggestNext(latest != null ? latest.getVersion() : null));
        }
        version.setIsActive(0);
        version.setCreatorId(resolveCurrentUserId());
        if (version.getCreatedAt() == null) {
            version.setCreatedAt(LocalDateTime.now());
        }
        versionMapper.insert(version);
        syncNodePermissionsFromDefinition(version.getId(), version.getDefinition());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateVersion(Long id, WorkflowVersion version) {
        WorkflowVersion existing = versionMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("工作流版本不存在");
        }

        if (version.getDefinition() != null) {
            validateDefinitionOrThrow(version.getDefinition());
            existing.setDefinition(version.getDefinition());
        }
        if (StringUtils.hasText(version.getName())) {
            existing.setName(version.getName());
        }

        versionMapper.updateById(existing);
        if (version.getDefinition() != null) {
            syncNodePermissionsFromDefinition(existing.getId(), existing.getDefinition());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void activateVersion(Long id, Long projectId) {
        WorkflowVersion version = versionMapper.selectById(id);
        if (version == null || !Objects.equals(version.getProjectId(), projectId)) {
            throw new BusinessException("工作流版本不存在");
        }

        long visualNodeCount = workflowNodeMapper.selectCount(new LambdaQueryWrapper<WorkflowNode>()
                .eq(WorkflowNode::getWorkflowVersionId, id));
        if (visualNodeCount > 0) {
            workflowActivationService.activate(id);
            return;
        }

        validateDefinitionOrThrow(version.getDefinition());
        syncNodePermissionsFromDefinition(version.getId(), version.getDefinition());
        publishVersionToRuntime(projectId, version.getDefinition());

        versionMapper.update(null, new UpdateWrapper<WorkflowVersion>()
                .eq("project_id", projectId)
                .set("is_active", 0));
        versionMapper.update(null, new UpdateWrapper<WorkflowVersion>()
                .eq("id", id)
                .set("is_active", 1));
    }

    @Override
    public List<String> validateWorkflow(String definition) {
        return workflowDefinitionEngine.validateDefinition(definition);
    }

    @Override
    public String resolveInitialStateName(Long projectId, Requirement requirement) {
        Optional<String> definitionDrivenState = workflowDefinitionEngine.resolveInitialStateName(projectId, requirement);
        if (definitionDrivenState.isPresent()) {
            return definitionDrivenState.get();
        }

        List<WorkflowState> states = getStates(projectId);
        if (states.isEmpty()) {
            return "新建";
        }

        Set<Long> targetStateIds = getTransitions(projectId).stream()
                .map(WorkflowTransition::getToStateId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return states.stream()
                .filter(state -> !targetStateIds.contains(state.getId()))
                .min(Comparator.comparing(WorkflowState::getSortOrder, Comparator.nullsLast(Integer::compareTo)))
                .or(() -> states.stream()
                        .min(Comparator.comparing(WorkflowState::getSortOrder, Comparator.nullsLast(Integer::compareTo))))
                .map(WorkflowState::getName)
                .orElse("新建");
    }

    private WorkflowState findStateByName(Long projectId, String status) {
        Long runtimeProjectId = workflowVersionResolver.resolveRuntimeProjectId(projectId);
        return stateMapper.selectOne(new LambdaQueryWrapper<WorkflowState>()
                .eq(WorkflowState::getProjectId, runtimeProjectId)
                .eq(WorkflowState::getName, status)
                .last("LIMIT 1"));
    }

    private void recordStatusHistory(Long requirementId, Long operatorId, String oldStatus, String newStatus) {
        RequirementHistory history = new RequirementHistory();
        history.setRequirementId(requirementId);
        history.setOperatorId(operatorId);
        history.setFieldName("status");
        history.setOldValue(oldStatus);
        history.setNewValue(newStatus);
        history.setCreatedAt(LocalDateTime.now());
        requirementHistoryMapper.insert(history);
    }

    private void sendStatusChangeNotifications(Requirement requirement, String newStatus, Long operatorId) {
        String title = "需求状态变更";
        String content = String.format("需求【%s】状态已变更为【%s】", requirement.getTitle(), newStatus);

        if (requirement.getCreatorId() != null && !requirement.getCreatorId().equals(operatorId)) {
            notificationService.sendNotification(requirement.getCreatorId(), title, content, "requirement", requirement.getId());
        }
        if (requirement.getAssigneeId() != null
                && !requirement.getAssigneeId().equals(operatorId)
                && !requirement.getAssigneeId().equals(requirement.getCreatorId())) {
            notificationService.sendNotification(requirement.getAssigneeId(), title, content, "requirement", requirement.getId());
        }
    }

    private void validateDefinitionOrThrow(String definition) {
        List<String> errors = validateWorkflow(definition);
        if (!errors.isEmpty()) {
            throw new BusinessException(String.join("；", errors));
        }
    }

    private void syncNodePermissionsFromDefinition(Long workflowVersionId, String definition) {
        if (workflowVersionId == null) {
            return;
        }

        nodePermissionMapper.delete(new LambdaQueryWrapper<WorkflowNodePermission>()
                .eq(WorkflowNodePermission::getWorkflowVersionId, workflowVersionId));

        if (!StringUtils.hasText(definition)) {
            return;
        }

        try {
            WorkflowDefinitionEngine.RuntimeCompilation runtimeCompilation =
                    workflowDefinitionEngine.compileRuntimeDefinition(definition);

            for (NodeConfigDTO node : runtimeCompilation.runtimeStates()) {
                if (node == null || !StringUtils.hasText(node.getName())) {
                    continue;
                }

                WorkflowNodePermission perm = new WorkflowNodePermission();
                perm.setWorkflowVersionId(workflowVersionId);
                perm.setNodeId(node.getName().trim());
                perm.setAllowedRoles(writeJson(node.getAllowedRoles()));
                perm.setAllowedUsers(writeJson(node.getAllowedUsers()));
                perm.setEditableFields(writeJson(node.getEditableFields()));
                perm.setRequiredFields(writeJson(node.getRequiredFields()));
                perm.setAvailableActions(writeJson(node.getAvailableActions()));

                if (!hasPermissionPayload(perm)) {
                    continue;
                }
                nodePermissionMapper.insert(perm);
            }
        } catch (IllegalArgumentException e) {
            log.warn("Failed to parse workflow definition for node permissions, version={}: {}", workflowVersionId, e.getMessage());
        }
    }

    private boolean hasPermissionPayload(WorkflowNodePermission permission) {
        return StringUtils.hasText(permission.getAllowedRoles())
                || StringUtils.hasText(permission.getAllowedUsers())
                || StringUtils.hasText(permission.getEditableFields())
                || StringUtils.hasText(permission.getRequiredFields())
                || StringUtils.hasText(permission.getAvailableActions());
    }

    private void publishVersionToRuntime(Long projectId, String definition) {
        WorkflowDefinitionEngine.RuntimeCompilation runtimeCompilation;
        try {
            runtimeCompilation = workflowDefinitionEngine.compileRuntimeDefinition(definition);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("工作流定义解析失败");
        }

        ensureCurrentStatusesSupported(projectId, runtimeCompilation);

        transitionMapper.delete(new LambdaQueryWrapper<WorkflowTransition>()
                .eq(WorkflowTransition::getProjectId, projectId));
        stateMapper.delete(new LambdaQueryWrapper<WorkflowState>()
                .eq(WorkflowState::getProjectId, projectId));

        Map<String, Long> stateIdByName = new LinkedHashMap<>();
        for (NodeConfigDTO node : runtimeCompilation.runtimeStates()) {
            WorkflowState state = new WorkflowState();
            state.setProjectId(projectId);
            state.setName(node.getName());
            state.setColor(StringUtils.hasText(node.getColor()) ? node.getColor() : "#409EFF");
            state.setIsFinal(Boolean.TRUE.equals(node.getIsFinal()) ? 1 : 0);
            state.setSortOrder(node.getSortOrder() == null ? 0 : node.getSortOrder());
            stateMapper.insert(state);
            stateIdByName.put(node.getName().trim(), state.getId());
        }

        for (WorkflowDefinitionEngine.ResolvedTransitionSpec transitionSpec : runtimeCompilation.transitions()) {
            Long fromStateId = stateIdByName.get(transitionSpec.fromStateName());
            Long toStateId = stateIdByName.get(transitionSpec.targetStateName());
            if (fromStateId == null || toStateId == null) {
                continue;
            }

            WorkflowTransition transition = new WorkflowTransition();
            transition.setProjectId(projectId);
            transition.setFromStateId(fromStateId);
            transition.setToStateId(toStateId);
            transition.setAllowedRoles(transitionSpec.allowedRolesJson());
            transition.setRequiredFields(transitionSpec.requiredFieldsJson());
            transition.setConditions(normalizeJsonObject(transitionSpec.conditionsJson()));
            transitionMapper.insert(transition);
        }
    }

    private void ensureCurrentStatusesSupported(Long projectId, WorkflowDefinitionEngine.RuntimeCompilation runtimeCompilation) {
        Set<String> stateNames = runtimeCompilation.runtimeStates().stream()
                .map(NodeConfigDTO::getName)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        List<String> currentStatuses = requirementMapper.selectList(new LambdaQueryWrapper<Requirement>()
                        .eq(Requirement::getProjectId, projectId)
                        .eq(Requirement::getDeletedAt, 0))
                .stream()
                .map(Requirement::getStatus)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();

        List<String> unsupported = currentStatuses.stream()
                .filter(status -> !stateNames.contains(status))
                .toList();

        if (!unsupported.isEmpty()) {
            throw new BusinessException("目标工作流缺少正在使用的状态: " + String.join("、", unsupported));
        }
    }

    private Long resolveCurrentUserId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException("未登录，无法保存工作流版本");
        }
        return userId;
    }

    private String normalizeId(String id) {
        return id == null ? null : id.trim();
    }

    private WorkflowTransition toRuntimeTransition(Long projectId,
                                                   WorkflowState currentState,
                                                   WorkflowDefinitionEngine.ResolvedTransitionSpec spec) {
        if (currentState == null || spec == null || !StringUtils.hasText(spec.targetStateName())) {
            return null;
        }

        WorkflowState targetState = findStateByName(projectId, spec.targetStateName());
        if (targetState == null) {
            return null;
        }

        WorkflowTransition transition = new WorkflowTransition();
        transition.setProjectId(projectId);
        transition.setFromStateId(currentState.getId());
        transition.setToStateId(targetState.getId());
        transition.setAllowedRoles(spec.allowedRolesJson());
        transition.setRequiredFields(spec.requiredFieldsJson());
        transition.setConditions(spec.conditionsJson());
        return transition;
    }

    private String normalizeJsonStringList(String raw, boolean defaultEmptyArray) {
        if (!StringUtils.hasText(raw)) {
            return defaultEmptyArray ? "[]" : null;
        }
        try {
            List<String> values = objectMapper.readValue(raw, STRING_LIST);
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception ignore) {
            List<String> values = Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .map(value -> value.replace("[", "").replace("]", "").replace("\"", ""))
                    .filter(StringUtils::hasText)
                    .toList();
            return writeJson(values);
        }
    }

    private String normalizeJsonObject(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(objectMapper.readTree(raw));
        } catch (Exception ignore) {
            return "{}";
        }
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Collection<?> collection && collection.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException("工作流配置序列化失败");
        }
    }
}
