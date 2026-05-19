package com.demand.system.module.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.requirement.entity.RequirementHistory;
import com.demand.system.module.requirement.mapper.RequirementHistoryMapper;
import com.demand.system.module.requirement.mapper.RequirementMapper;
import com.demand.system.module.project.entity.Project;
import com.demand.system.module.project.mapper.ProjectMapper;
import com.demand.system.module.user.entity.User;
import com.demand.system.module.user.mapper.UserMapper;
import com.demand.system.module.rbac.entity.Role;
import com.demand.system.module.rbac.mapper.RoleMapper;
import com.demand.system.module.workflow.dto.AvailableTransitionDTO;
import com.demand.system.module.workflow.dto.FlowTransitionRequest;
import com.demand.system.module.workflow.dto.TransitionVO;
import com.demand.system.module.workflow.dto.WorkflowAvailableActionsDTO;
import com.demand.system.module.workflow.entity.*;
import com.demand.system.module.workflow.mapper.WorkflowEdgeMapper;
import com.demand.system.module.workflow.mapper.WorkflowInstanceMapper;
import com.demand.system.module.workflow.mapper.WorkflowInstanceTransitionMapper;
import com.demand.system.module.workflow.mapper.WorkflowNodeMapper;
import com.demand.system.module.workflow.mapper.NodeStatusMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class WorkflowEngineService {

    private final WorkflowInstanceMapper instanceMapper;
    private final WorkflowInstanceTransitionMapper transitionMapper;
    private final WorkflowNodeMapper nodeMapper;
    private final WorkflowEdgeMapper edgeMapper;
    private final RequirementMapper requirementMapper;
    private final RequirementHistoryMapper requirementHistoryMapper;
    private final ProjectMapper projectMapper;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final NodeStatusMapper nodeStatusMapper;

    public WorkflowEngineService(WorkflowInstanceMapper instanceMapper, WorkflowInstanceTransitionMapper transitionMapper,
                               WorkflowNodeMapper nodeMapper, WorkflowEdgeMapper edgeMapper,
                               RequirementMapper requirementMapper, RequirementHistoryMapper requirementHistoryMapper,
                               ProjectMapper projectMapper, UserMapper userMapper, RoleMapper roleMapper,
                               NodeStatusMapper nodeStatusMapper) {
        this.instanceMapper = instanceMapper;
        this.transitionMapper = transitionMapper;
        this.nodeMapper = nodeMapper;
        this.edgeMapper = edgeMapper;
        this.requirementMapper = requirementMapper;
        this.requirementHistoryMapper = requirementHistoryMapper;
        this.projectMapper = projectMapper;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.nodeStatusMapper = nodeStatusMapper;
    }

    @Transactional
    public void initWorkflow(Long requirementId, Long workflowVersionId) {
        WorkflowNode startNode = nodeMapper.selectOne(
            new LambdaQueryWrapper<WorkflowNode>()
                .eq(WorkflowNode::getWorkflowVersionId, workflowVersionId)
                .eq(WorkflowNode::getNodeType, "start")
        );
        if (startNode == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "工作流缺少开始节点");
        }

        WorkflowInstance instance = new WorkflowInstance();
        instance.setRequirementId(requirementId);
        instance.setWorkflowVersionId(workflowVersionId);
        instance.setCurrentNodeId(startNode.getNodeId());
        instance.setStatus("running");
        instanceMapper.insert(instance);

        WorkflowInstanceTransition transition = new WorkflowInstanceTransition();
        transition.setInstanceId(instance.getId());
        transition.setRequirementId(requirementId);
        transition.setToNodeId(startNode.getNodeId());
        transition.setToNodeName(startNode.getNodeName());
        transition.setOperatorId(SecurityUtils.getCurrentUserId());
        transition.setAction("submit");
        transition.setStartedAt(LocalDateTime.now());
        transitionMapper.insert(transition);

        String startNodeStatusCode = resolveNodeStatusCode(startNode);
        requirementMapper.update(null, new LambdaUpdateWrapper<Requirement>()
            .eq(Requirement::getId, requirementId)
            .set(Requirement::getWorkflowInstanceId, instance.getId())
            .set(Requirement::getStatus, resolveNodeStatusName(startNodeStatusCode))
            .set(Requirement::getNodeStatus, startNodeStatusCode)
            .set(Requirement::getIsDraft, false)
        );
    }

    @Transactional
    public void transition(FlowTransitionRequest request) {
        Long operatorId = SecurityUtils.getCurrentUserId();
        Requirement requirement = requirementMapper.selectById(request.getRequirementId());
        if (requirement == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "需求不存在");
        }

        WorkflowInstance instance = instanceMapper.selectOne(
            new LambdaQueryWrapper<WorkflowInstance>()
                .eq(WorkflowInstance::getRequirementId, request.getRequirementId())
        );
        if (instance == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "工作流实例不存在");
        }
        if ("completed".equals(instance.getStatus()) || "cancelled".equals(instance.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "工作流已结束，无法流转");
        }

        WorkflowNode currentNode = getNode(instance.getWorkflowVersionId(), instance.getCurrentNodeId());
        WorkflowNode targetNode = getNode(instance.getWorkflowVersionId(), request.getToNodeId());
        bindProjectIfNecessary(requirement, request.getProjectId(), operatorId);
        requirement = requirementMapper.selectById(request.getRequirementId());

        validateTransition(instance, currentNode, targetNode, request.getAction(), operatorId);

        closeCurrentTransition(instance.getId());

        WorkflowInstanceTransition newTransition = new WorkflowInstanceTransition();
        newTransition.setInstanceId(instance.getId());
        newTransition.setRequirementId(request.getRequirementId());
        newTransition.setFromNodeId(instance.getCurrentNodeId());
        newTransition.setFromNodeName(currentNode != null ? currentNode.getNodeName() : "");
        newTransition.setToNodeId(request.getToNodeId());
        newTransition.setToNodeName(targetNode.getNodeName());
        newTransition.setOperatorId(operatorId);
        newTransition.setAction(request.getAction() != null ? request.getAction() : "submit");
        newTransition.setComment(request.getComment());
        newTransition.setStartedAt(LocalDateTime.now());
        transitionMapper.insert(newTransition);

        String newStatus = "running";
        if ("cancel".equals(request.getAction())) {
            newStatus = "cancelled";
        } else if (targetNode != null && "end".equals(targetNode.getNodeType())) {
            newStatus = "completed";
        }

        instanceMapper.update(null, new LambdaUpdateWrapper<WorkflowInstance>()
            .eq(WorkflowInstance::getId, instance.getId())
            .set(WorkflowInstance::getPreviousNodeId, instance.getCurrentNodeId())
            .set(WorkflowInstance::getCurrentNodeId, request.getToNodeId())
            .set(WorkflowInstance::getStatus, newStatus)
        );

        String nodeStatusCode = resolveNodeStatusCode(targetNode);
        requirementMapper.update(null, new LambdaUpdateWrapper<Requirement>()
            .eq(Requirement::getId, request.getRequirementId())
            .set(Requirement::getStatus, resolveNodeStatusName(nodeStatusCode))
            .set(Requirement::getNodeStatus, nodeStatusCode)
        );
    }

    @Transactional
    public void rollback(Long requirementId, String comment) {
        Long operatorId = SecurityUtils.getCurrentUserId();
        WorkflowInstance instance = getRunningInstance(requirementId);

        if (instance.getPreviousNodeId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已在开始节点，无法回退");
        }

        WorkflowNode currentNode = getNode(instance.getWorkflowVersionId(), instance.getCurrentNodeId());
        validatePermission(currentNode, operatorId);
        if ("end".equals(currentNode.getNodeType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "结束节点不可回退");
        }

        WorkflowNode previousNode = getNode(instance.getWorkflowVersionId(), instance.getPreviousNodeId());

        closeCurrentTransition(instance.getId());

        WorkflowInstanceTransition rollbackTransition = new WorkflowInstanceTransition();
        rollbackTransition.setInstanceId(instance.getId());
        rollbackTransition.setRequirementId(requirementId);
        rollbackTransition.setFromNodeId(instance.getCurrentNodeId());
        rollbackTransition.setFromNodeName(currentNode.getNodeName());
        rollbackTransition.setToNodeId(instance.getPreviousNodeId());
        rollbackTransition.setToNodeName(previousNode != null ? previousNode.getNodeName() : "");
        rollbackTransition.setOperatorId(operatorId);
        rollbackTransition.setAction("rollback");
        rollbackTransition.setComment(comment);
        rollbackTransition.setStartedAt(LocalDateTime.now());
        transitionMapper.insert(rollbackTransition);

        instanceMapper.update(null, new LambdaUpdateWrapper<WorkflowInstance>()
            .eq(WorkflowInstance::getId, instance.getId())
            .set(WorkflowInstance::getCurrentNodeId, instance.getPreviousNodeId())
            .set(WorkflowInstance::getPreviousNodeId, instance.getCurrentNodeId())
        );

        String nodeStatusCode = resolveNodeStatusCode(previousNode);
        requirementMapper.update(null, new LambdaUpdateWrapper<Requirement>()
            .eq(Requirement::getId, requirementId)
            .set(Requirement::getStatus, resolveNodeStatusName(nodeStatusCode))
            .set(Requirement::getNodeStatus, nodeStatusCode)
        );
    }

    @Transactional
    public void cancel(Long requirementId, String comment) {
        Long operatorId = SecurityUtils.getCurrentUserId();
        Requirement requirement = requirementMapper.selectById(requirementId);
        if (requirement == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "需求不存在");
        }
        WorkflowInstance instance = getRunningInstance(requirementId);

        WorkflowNode currentNode = getNode(instance.getWorkflowVersionId(), instance.getCurrentNodeId());
        if ("end".equals(currentNode.getNodeType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "结束节点不可取消");
        }
        if (!canCancelRequirement(requirement, currentNode, operatorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "您没有权限取消当前需求");
        }
        if (comment == null || comment.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "取消原因不能为空");
        }

        closeCurrentTransition(instance.getId());

        WorkflowInstanceTransition cancelTransition = new WorkflowInstanceTransition();
        cancelTransition.setInstanceId(instance.getId());
        cancelTransition.setRequirementId(requirementId);
        cancelTransition.setFromNodeId(instance.getCurrentNodeId());
        cancelTransition.setFromNodeName(currentNode.getNodeName());
        cancelTransition.setToNodeId("cancelled");
        cancelTransition.setToNodeName("已取消");
        cancelTransition.setOperatorId(operatorId);
        cancelTransition.setAction("cancel");
        cancelTransition.setComment(comment);
        cancelTransition.setStartedAt(LocalDateTime.now());
        transitionMapper.insert(cancelTransition);

        instanceMapper.update(null, new LambdaUpdateWrapper<WorkflowInstance>()
            .eq(WorkflowInstance::getId, instance.getId())
            .set(WorkflowInstance::getStatus, "cancelled")
        );

        requirementMapper.update(null, new LambdaUpdateWrapper<Requirement>()
            .eq(Requirement::getId, requirementId)
            .set(Requirement::getStatus, resolveNodeStatusName("CANCELLED"))
            .set(Requirement::getNodeStatus, "CANCELLED")
        );
    }

    public void saveDraft(Long requirementId) {
        requirementMapper.update(null, new LambdaUpdateWrapper<Requirement>()
            .eq(Requirement::getId, requirementId)
            .set(Requirement::getIsDraft, true)
        );
    }

    public WorkflowAvailableActionsDTO getAvailableActions(Long requirementId) {
        WorkflowAvailableActionsDTO actions = new WorkflowAvailableActionsDTO();
        actions.setCanTransition(false);
        actions.setCanRollback(false);
        actions.setCanCancel(false);
        actions.setTransitions(Collections.emptyList());

        Requirement requirement = requirementMapper.selectById(requirementId);
        if (requirement == null || requirement.getWorkflowInstanceId() == null) {
            return actions;
        }

        WorkflowInstance instance = instanceMapper.selectOne(
                new LambdaQueryWrapper<WorkflowInstance>()
                        .eq(WorkflowInstance::getRequirementId, requirementId)
        );
        if (instance == null || !"running".equals(instance.getStatus())) {
            return actions;
        }

        WorkflowNode currentNode = getNode(instance.getWorkflowVersionId(), instance.getCurrentNodeId());
        if (currentNode == null) {
            return actions;
        }

        Long operatorId = SecurityUtils.getCurrentUserId();
        boolean canOperate = hasOperatePermission(currentNode, operatorId);
        boolean canCancel = canCancelRequirement(requirement, currentNode, operatorId);

        List<AvailableTransitionDTO> transitions = Collections.emptyList();
        if (canOperate) {
            transitions = edgeMapper.selectList(
                    new LambdaQueryWrapper<WorkflowEdge>()
                            .eq(WorkflowEdge::getWorkflowVersionId, instance.getWorkflowVersionId())
                            .eq(WorkflowEdge::getSourceNodeId, instance.getCurrentNodeId())
            ).stream().map(edge -> {
                WorkflowNode targetNode = getNode(instance.getWorkflowVersionId(), edge.getTargetNodeId());
                if (targetNode == null) {
                    return null;
                }
                AvailableTransitionDTO dto = new AvailableTransitionDTO();
                dto.setToNodeId(targetNode.getNodeId());
                dto.setToNodeName(targetNode.getNodeName());
                dto.setLabel(edge.getLabel());
                String nodeStatusCode = resolveNodeStatusCode(targetNode);
                dto.setBindStatusCode(nodeStatusCode);
                dto.setBindStatusName(resolveNodeStatusName(nodeStatusCode));
                dto.setProjectRequired(isProjectRequired(targetNode));
                return dto;
            }).filter(java.util.Objects::nonNull).collect(Collectors.toList());
        }

        actions.setTransitions(transitions);
        actions.setCanTransition(canOperate && !transitions.isEmpty());
        actions.setCanRollback(canOperate && instance.getPreviousNodeId() != null && !"end".equals(currentNode.getNodeType()));
        actions.setCanCancel(canCancel);
        return actions;
    }

    public List<TransitionVO> getTransitionHistory(Long requirementId) {
        List<WorkflowInstanceTransition> transitions = transitionMapper.selectList(
            new LambdaQueryWrapper<WorkflowInstanceTransition>()
                .eq(WorkflowInstanceTransition::getRequirementId, requirementId)
                .orderByAsc(WorkflowInstanceTransition::getCreatedAt)
        );

        return transitions.stream().map(t -> {
            TransitionVO vo = new TransitionVO();
            vo.setId(t.getId());
            vo.setInstanceId(t.getInstanceId());
            vo.setRequirementId(t.getRequirementId());
            vo.setFromNodeId(t.getFromNodeId());
            vo.setFromNodeName(t.getFromNodeName());
            vo.setToNodeId(t.getToNodeId());
            vo.setToNodeName(t.getToNodeName());
            vo.setOperatorId(t.getOperatorId());
            vo.setAction(t.getAction());
            vo.setComment(t.getComment());
            vo.setStartedAt(t.getStartedAt());
            vo.setCompletedAt(t.getCompletedAt());
            vo.setDurationSeconds(t.getDurationSeconds());
            vo.setDurationDisplay(formatDuration(t.getDurationSeconds()));
            vo.setCreatedAt(t.getCreatedAt());

            if (t.getOperatorId() != null) {
                User user = userMapper.selectById(t.getOperatorId());
                if (user != null) {
                    vo.setOperatorName(user.getRealName());
                }
            }
            return vo;
        }).collect(Collectors.toList());
    }

    private void validateTransition(WorkflowInstance instance, WorkflowNode currentNode,
                                     WorkflowNode targetNode, String action, Long operatorId) {
        if ("cancel".equals(action)) {
            return;
        }

        boolean edgeExists = edgeMapper.selectCount(
            new LambdaQueryWrapper<WorkflowEdge>()
                .eq(WorkflowEdge::getWorkflowVersionId, instance.getWorkflowVersionId())
                .eq(WorkflowEdge::getSourceNodeId, instance.getCurrentNodeId())
                .eq(WorkflowEdge::getTargetNodeId, targetNode.getNodeId())
        ) > 0;

        if (!edgeExists && !"rollback".equals(action)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不允许从当前节点流转到目标节点");
        }

        if (currentNode.getAssigneeType() != null) {
            validatePermission(currentNode, operatorId);
        }

        if (isProjectRequired(targetNode)) {
            Requirement requirement = requirementMapper.selectById(instance.getRequirementId());
            if (requirement == null || requirement.getProjectId() == null || requirement.getProjectId() <= 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "目标节点要求必须绑定项目后才能提交流转");
            }
        }
    }

    private void validatePermission(WorkflowNode node, Long operatorId) {
        String assigneeType = node.getAssigneeType();
        if ("SPECIFIED_USER".equals(assigneeType)) {
            if (node.getAssigneeUserIds() == null || !node.getAssigneeUserIds().contains(operatorId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "您没有权限操作此节点");
            }
        } else if ("SPECIFIED_ROLE".equals(assigneeType)) {
            Integer roleId = node.getAssigneeRoleId();
            if (roleId == null) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "您没有权限操作此节点");
            }
            Role role = roleMapper.selectById(roleId.longValue());
            if (role == null || role.getCode() == null || role.getCode().isBlank()) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "您没有权限操作此节点");
            }
            if (!SecurityUtils.getCurrentUserRoles().contains(role.getCode().trim())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "您没有权限操作此节点");
            }
        }
        // 争抢式：绑定角色时所有有该角色的人可见可操作，谁先流转归谁
    }

    private boolean hasOperatePermission(WorkflowNode node, Long operatorId) {
        try {
            validatePermission(node, operatorId);
            return true;
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.FORBIDDEN) {
                return false;
            }
            throw ex;
        }
    }

    private void bindProjectIfNecessary(Requirement requirement, Long requestedProjectId, Long operatorId) {
        Long normalizedRequestedProjectId = normalizeProjectId(requestedProjectId);
        Long currentProjectId = normalizeProjectId(requirement.getProjectId());

        if (normalizedRequestedProjectId <= 0) {
            return;
        }
        if (currentProjectId > 0 && !Objects.equals(currentProjectId, normalizedRequestedProjectId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "需求已绑定项目，不允许在流转时修改");
        }
        ensureProjectCanBeBound(normalizedRequestedProjectId);
        if (currentProjectId > 0) {
            return;
        }

        requirementMapper.update(null, new LambdaUpdateWrapper<Requirement>()
                .eq(Requirement::getId, requirement.getId())
                .set(Requirement::getProjectId, normalizedRequestedProjectId));
        recordProjectBindingHistory(requirement.getId(), operatorId, normalizedRequestedProjectId);
    }

    private boolean canCancelRequirement(Requirement requirement, WorkflowNode currentNode, Long operatorId) {
        if (currentNode == null || "end".equals(currentNode.getNodeType())) {
            return false;
        }
        if (!isCancelAllowed(currentNode)) {
            return false;
        }
        if (isCreatorOrAdmin(requirement, operatorId)) {
            return true;
        }
        return hasOperatePermission(currentNode, operatorId);
    }

    private boolean isCreatorOrAdmin(Requirement requirement, Long operatorId) {
        if (requirement != null && requirement.getCreatorId() != null && requirement.getCreatorId().equals(operatorId)) {
            return true;
        }
        return SecurityUtils.getCurrentUserRoles().contains("admin");
    }

    private boolean isCancelAllowed(WorkflowNode node) {
        return readNodeBooleanProperty(node, "allowCancel", true);
    }

    private boolean isProjectRequired(WorkflowNode node) {
        return readNodeBooleanProperty(node, "projectRequired", false);
    }

    private boolean readNodeBooleanProperty(WorkflowNode node, String key, boolean defaultValue) {
        if (node == null || node.getProperties() == null) {
            return defaultValue;
        }

        Object directValue = node.getProperties().get(key);
        if (directValue != null) {
            return parseBooleanValue(directValue, defaultValue);
        }

        Object nestedProperties = node.getProperties().get("properties");
        if (nestedProperties instanceof java.util.Map<?, ?> nestedMap) {
            Object nestedValue = nestedMap.get(key);
            if (nestedValue != null) {
                return parseBooleanValue(nestedValue, defaultValue);
            }
        }
        return defaultValue;
    }

    private boolean parseBooleanValue(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue() != 0;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(text);
    }

    private void ensureProjectCanBeBound(Long projectId) {
        if (projectId == null || projectId <= 0) {
            return;
        }

        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "所选项目不存在");
        }
        if (isProjectExpired(project)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已截止项目不可绑定到需求");
        }
    }

    private boolean isProjectExpired(Project project) {
        if (project == null) {
            return true;
        }
        if ("expired".equalsIgnoreCase(project.getStatus())) {
            return true;
        }
        return project.getEndDate() != null && project.getEndDate().isBefore(LocalDate.now());
    }

    private Long normalizeProjectId(Long projectId) {
        return projectId == null || projectId <= 0 ? 0L : projectId;
    }

    private void recordProjectBindingHistory(Long requirementId, Long operatorId, Long projectId) {
        RequirementHistory history = new RequirementHistory();
        history.setRequirementId(requirementId);
        history.setOperatorId(operatorId);
        history.setFieldName("projectId");
        history.setOldValue("未绑定");
        history.setNewValue("绑定项目#" + projectId);
        history.setCreatedAt(LocalDateTime.now());
        requirementHistoryMapper.insert(history);
    }

    private void closeCurrentTransition(Long instanceId) {
        WorkflowInstanceTransition lastTransition = transitionMapper.selectOne(
            new LambdaQueryWrapper<WorkflowInstanceTransition>()
                .eq(WorkflowInstanceTransition::getInstanceId, instanceId)
                .isNull(WorkflowInstanceTransition::getCompletedAt)
                .orderByDesc(WorkflowInstanceTransition::getId)
                .last("LIMIT 1")
        );
        if (lastTransition != null) {
            LocalDateTime now = LocalDateTime.now();
            long seconds = Duration.between(lastTransition.getStartedAt(), now).getSeconds();
            transitionMapper.update(null, new LambdaUpdateWrapper<WorkflowInstanceTransition>()
                .eq(WorkflowInstanceTransition::getId, lastTransition.getId())
                .set(WorkflowInstanceTransition::getCompletedAt, now)
                .set(WorkflowInstanceTransition::getDurationSeconds, seconds)
            );
        }
    }

    private WorkflowNode getNode(Long versionId, String nodeId) {
        return nodeMapper.selectOne(
            new LambdaQueryWrapper<WorkflowNode>()
                .eq(WorkflowNode::getWorkflowVersionId, versionId)
                .eq(WorkflowNode::getNodeId, nodeId)
        );
    }

    private WorkflowInstance getRunningInstance(Long requirementId) {
        WorkflowInstance instance = instanceMapper.selectOne(
            new LambdaQueryWrapper<WorkflowInstance>()
                .eq(WorkflowInstance::getRequirementId, requirementId)
        );
        if (instance == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "工作流实例不存在");
        }
        if (!"running".equals(instance.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "工作流已结束");
        }
        return instance;
    }

    private String resolveNodeStatusCode(WorkflowNode node) {
        if (node == null) return "DRAFT";
        if (node.getProperties() != null) {
            Object nodeStatusCode = node.getProperties().get("nodeStatusCode");
            if (nodeStatusCode != null) {
                return nodeStatusCode.toString();
            }

            Object nestedProperties = node.getProperties().get("properties");
            if (nestedProperties instanceof java.util.Map<?, ?> nestedMap) {
                Object nestedNodeStatusCode = nestedMap.get("nodeStatusCode");
                if (nestedNodeStatusCode != null) {
                    return nestedNodeStatusCode.toString();
                }
            }
        }
        return "DRAFT";
    }

    private String resolveNodeStatusName(String nodeStatusCode) {
        if (nodeStatusCode == null || nodeStatusCode.isBlank()) {
            return "新建";
        }
        NodeStatus nodeStatus = nodeStatusMapper.selectOne(
            new LambdaQueryWrapper<NodeStatus>()
                .eq(NodeStatus::getCode, nodeStatusCode)
                .last("LIMIT 1")
        );
        if (nodeStatus != null && nodeStatus.getName() != null && !nodeStatus.getName().isBlank()) {
            return nodeStatus.getName();
        }
        return nodeStatusCode;
    }

    private String formatDuration(Long seconds) {
        if (seconds == null) return "-";
        if (seconds < 60) return seconds + "秒";
        if (seconds < 3600) return (seconds / 60) + "分" + (seconds % 60) + "秒";
        if (seconds < 86400) return (seconds / 3600) + "时" + ((seconds % 3600) / 60) + "分";
        if (seconds < 2592000L) return (seconds / 86400) + "天" + ((seconds % 86400) / 3600) + "时";
        if (seconds < 31536000L) return (seconds / 2592000L) + "月" + ((seconds % 2592000L) / 86400) + "天";
        return (seconds / 31536000L) + "年" + ((seconds % 31536000L) / 2592000L) + "月";
    }
}
