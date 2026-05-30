package com.demand.system.module.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.organization.entity.SysOrg;
import com.demand.system.module.organization.mapper.SysOrgMapper;
import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.requirement.entity.RequirementHistory;
import com.demand.system.module.requirement.mapper.RequirementHistoryMapper;
import com.demand.system.module.requirement.mapper.RequirementMapper;
import com.demand.system.module.requirement.service.RequirementApprovalEvaluationService;
import com.demand.system.module.project.entity.Project;
import com.demand.system.module.project.mapper.ProjectMapper;
import com.demand.system.module.user.entity.User;
import com.demand.system.module.user.entity.UserOrganization;
import com.demand.system.module.user.mapper.UserMapper;
import com.demand.system.module.user.mapper.UserOrganizationMapper;
import com.demand.system.module.rbac.entity.Role;
import com.demand.system.module.rbac.entity.RoleGroup;
import com.demand.system.module.rbac.mapper.RoleMapper;
import com.demand.system.module.rbac.mapper.RoleGroupMapper;
import com.demand.system.module.workflow.dto.ParallelBranchVO;
import com.demand.system.module.workflow.dto.AvailableTransitionDTO;
import com.demand.system.module.workflow.dto.FlowTransitionRequest;
import com.demand.system.module.workflow.dto.TransitionVO;
import com.demand.system.module.workflow.dto.WorkflowAvailableActionsDTO;
import com.demand.system.module.workflow.engine.WorkflowGraphContext;
import com.demand.system.module.workflow.engine.WorkflowGraphNavigator;
import com.demand.system.module.workflow.engine.WorkflowRuntimeLoader;
import com.demand.system.module.workflow.entity.*;
import com.demand.system.module.workflow.mapper.WorkflowEdgeMapper;
import com.demand.system.module.workflow.mapper.WorkflowInstanceMapper;
import com.demand.system.module.workflow.mapper.WorkflowInstanceTransitionMapper;
import com.demand.system.module.workflow.mapper.WorkflowNodeMapper;
import com.demand.system.module.workflow.mapper.NodeStatusMapper;
import com.demand.system.module.workflow.support.WorkflowNodeUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private final RoleGroupMapper roleGroupMapper;
    private final UserOrganizationMapper userOrganizationMapper;
    private final SysOrgMapper sysOrgMapper;
    private final NodeStatusMapper nodeStatusMapper;
    private final WorkflowGraphNavigator graphNavigator;
    private final WorkflowRuntimeLoader runtimeLoader;
    private final WorkflowRuntimeMigrationService workflowRuntimeMigrationService;
    private final WorkflowNotificationService notificationService;
    private final RequirementApprovalEvaluationService approvalEvaluationService;
    private final WorkflowCountersignService countersignService;
    private final WorkflowParallelBranchService parallelBranchService;

    public WorkflowEngineService(WorkflowInstanceMapper instanceMapper, WorkflowInstanceTransitionMapper transitionMapper,
                               WorkflowNodeMapper nodeMapper, WorkflowEdgeMapper edgeMapper,
                               RequirementMapper requirementMapper, RequirementHistoryMapper requirementHistoryMapper,
                               ProjectMapper projectMapper, UserMapper userMapper, RoleMapper roleMapper,
                               RoleGroupMapper roleGroupMapper, UserOrganizationMapper userOrganizationMapper,
                               SysOrgMapper sysOrgMapper,
                               NodeStatusMapper nodeStatusMapper, WorkflowGraphNavigator graphNavigator,
                               WorkflowRuntimeLoader runtimeLoader, WorkflowRuntimeMigrationService workflowRuntimeMigrationService,
                               WorkflowNotificationService notificationService,
                               RequirementApprovalEvaluationService approvalEvaluationService,
                               @Lazy WorkflowCountersignService countersignService,
                               WorkflowParallelBranchService parallelBranchService) {
        this.instanceMapper = instanceMapper;
        this.transitionMapper = transitionMapper;
        this.nodeMapper = nodeMapper;
        this.edgeMapper = edgeMapper;
        this.requirementMapper = requirementMapper;
        this.requirementHistoryMapper = requirementHistoryMapper;
        this.projectMapper = projectMapper;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.roleGroupMapper = roleGroupMapper;
        this.userOrganizationMapper = userOrganizationMapper;
        this.sysOrgMapper = sysOrgMapper;
        this.nodeStatusMapper = nodeStatusMapper;
        this.graphNavigator = graphNavigator;
        this.runtimeLoader = runtimeLoader;
        this.workflowRuntimeMigrationService = workflowRuntimeMigrationService;
        this.notificationService = notificationService;
        this.approvalEvaluationService = approvalEvaluationService;
        this.countersignService = countersignService;
        this.parallelBranchService = parallelBranchService;
    }

    @Transactional
    public void submitFromDraft(Long requirementId, Long workflowVersionId, String targetNodeId,
                                Long projectId, String comment, Long operatorId) {
        Requirement requirement = requirementMapper.selectById(requirementId);
        if (requirement == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "需求不存在");
        }
        if (requirement.getWorkflowInstanceId() != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "需求已提交，无需重复提交");
        }

        WorkflowGraphContext context = runtimeLoader.loadContext(workflowVersionId);
        WorkflowNode startNode = context.nodesById().values().stream()
                .filter(node -> "start".equalsIgnoreCase(node.getNodeType()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "工作流缺少开始节点"));

        WorkflowNode targetNode = context.getNode(targetNodeId);
        if (targetNode == null || !WorkflowNodeUtils.isWaitNode(targetNode.getNodeType())
                || "start".equalsIgnoreCase(targetNode.getNodeType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "目标节点非法");
        }

        List<String> path = graphNavigator.resolvePathToWaitNode(context, startNode.getNodeId(), targetNodeId, requirement);
        if (path.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无法从开始节点到达目标节点");
        }

        bindProjectIfNecessary(requirement, projectId, operatorId);
        requirement = requirementMapper.selectById(requirementId);

        WorkflowInstance instance = new WorkflowInstance();
        instance.setRequirementId(requirementId);
        instance.setWorkflowVersionId(workflowVersionId);
        instance.setCurrentNodeId(targetNodeId);
        instance.setPreviousNodeId(startNode.getNodeId());
        instance.setStatus("end".equalsIgnoreCase(targetNode.getNodeType()) ? "completed" : "running");
        instance.setLockVersion(0);
        instanceMapper.insert(instance);

        WorkflowInstanceTransition transition = new WorkflowInstanceTransition();
        transition.setInstanceId(instance.getId());
        transition.setRequirementId(requirementId);
        transition.setFromNodeId(startNode.getNodeId());
        transition.setFromNodeName(startNode.getNodeName());
        transition.setToNodeId(targetNodeId);
        transition.setToNodeName(targetNode.getNodeName());
        transition.setOperatorId(operatorId);
        transition.setAction("submit");
        transition.setComment(comment);
        transition.setStartedAt(LocalDateTime.now());
        transitionMapper.insert(transition);
        approvalEvaluationService.saveOnTransition(instance, targetNode, transition.getId(), operatorId, comment);

        String nodeStatusCode = resolveNodeStatusCode(targetNode);
        requirementMapper.update(null, new LambdaUpdateWrapper<Requirement>()
                .eq(Requirement::getId, requirementId)
                .set(Requirement::getWorkflowInstanceId, instance.getId())
                .set(Requirement::getStatus, resolveNodeStatusName(nodeStatusCode))
                .set(Requirement::getNodeStatus, nodeStatusCode)
                .set(Requirement::getIsDraft, false));

        notificationService.notifyNodeEntered(requirement, targetNode, operatorId);
        initCountersignIfNeeded(instance.getId(), targetNode, requirement);
    }

    @Transactional
    public void initWorkflow(Long requirementId, Long workflowVersionId) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "请使用 submitFromDraft 提交流程");
    }

    @Transactional
    public void transition(FlowTransitionRequest request) {
        workflowRuntimeMigrationService.alignRequirementInstanceIfNeeded(request.getRequirementId());
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
        if (request.getLockVersion() != null && !Objects.equals(request.getLockVersion(), instance.getLockVersion())) {
            throw conflictException(instance.getId());
        }

        WorkflowGraphContext context = runtimeLoader.loadContext(instance.getWorkflowVersionId());
        WorkflowNode currentNode = context.getNode(instance.getCurrentNodeId());
        WorkflowNode targetNode = context.getNode(request.getToNodeId());
        bindProjectIfNecessary(requirement, request.getProjectId(), operatorId);
        requirement = requirementMapper.selectById(request.getRequirementId());

        validateTransition(context, instance, currentNode, targetNode, request.getAction(), operatorId);
        if (isCountersignEnabled(currentNode)
                && !countersignService.canProceedAfterCountersign(instance.getId(), instance.getCurrentNodeId(), currentNode)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "会签尚未完成，无法流转");
        }
        boolean approvalEvaluationRequired = isApprovalEvaluationRequired(currentNode);
        if (approvalEvaluationRequired) {
            validateApprovalEvaluation(request.getRating(), request.getComment());
        }

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

        if (approvalEvaluationRequired && currentNode != null) {
            approvalEvaluationService.saveOnApprovalTransition(
                    instance, currentNode, newTransition.getId(), operatorId, request.getRating(), request.getComment());
        } else {
            approvalEvaluationService.saveOnTransition(
                    instance, currentNode != null ? currentNode : targetNode, newTransition.getId(), operatorId, request.getComment());
        }

        String newStatus = "running";
        if ("cancel".equals(request.getAction())) {
            newStatus = "cancelled";
        } else if (targetNode != null && "end".equalsIgnoreCase(targetNode.getNodeType())) {
            newStatus = "completed";
        }

        Integer currentLockVersion = instance.getLockVersion() == null ? 0 : instance.getLockVersion();
        int updated = instanceMapper.update(null, new LambdaUpdateWrapper<WorkflowInstance>()
            .eq(WorkflowInstance::getId, instance.getId())
            .eq(WorkflowInstance::getLockVersion, currentLockVersion)
            .set(WorkflowInstance::getPreviousNodeId, instance.getCurrentNodeId())
            .set(WorkflowInstance::getCurrentNodeId, request.getToNodeId())
            .set(WorkflowInstance::getStatus, newStatus)
            .set(WorkflowInstance::getLockVersion, currentLockVersion + 1)
        );
        if (updated <= 0) {
            throw conflictException(instance.getId());
        }

        String nodeStatusCode = resolveNodeStatusCode(targetNode);
        requirementMapper.update(null, new LambdaUpdateWrapper<Requirement>()
            .eq(Requirement::getId, request.getRequirementId())
            .set(Requirement::getStatus, resolveNodeStatusName(nodeStatusCode))
            .set(Requirement::getNodeStatus, nodeStatusCode)
        );

        notificationService.notifyNodeEntered(requirement, targetNode, operatorId);

        instance = instanceMapper.selectById(instance.getId());
        parallelBranchService.initParallelBranchesIfNeeded(instance, context, request.getToNodeId(), requirement);
        parallelBranchService.afterTransition(instance, context, instance.getPreviousNodeId(), request.getToNodeId(), requirement);
        initCountersignIfNeeded(instance.getId(), targetNode, requirement);
    }

    /**
     * 初始化会签记录
     */
    private void initCountersignIfNeeded(Long instanceId, WorkflowNode targetNode, Requirement requirement) {
        if (targetNode == null || !"approval".equalsIgnoreCase(targetNode.getNodeType())) {
            return;
        }

        Map<String, Object> properties = targetNode.getProperties();
        if (properties == null) {
            return;
        }

        Boolean countersignEnabled = (Boolean) properties.get("countersignEnabled");
        if (countersignEnabled == null || !countersignEnabled) {
            return;
        }

        // 获取会签人列表
        List<Long> approverIds = resolveCountersignApprovers(targetNode, requirement);
        if (approverIds == null || approverIds.isEmpty()) {
            return;
        }

        countersignService.initCountersignRecords(instanceId, targetNode.getNodeId(), approverIds);
    }

    /**
     * 解析会签人列表
     */
    private List<Long> resolveCountersignApprovers(WorkflowNode node, Requirement requirement) {
        Map<String, Object> properties = node.getProperties();
        if (properties == null) {
            return Collections.emptyList();
        }

        String countersignMode = (String) properties.get("countersignMode");
        if ("FIXED".equals(countersignMode)) {
            @SuppressWarnings("unchecked")
            List<Long> fixedApprovers = (List<Long>) properties.get("countersignApprovers");
            return fixedApprovers != null ? fixedApprovers : Collections.emptyList();
        }

        // DYNAMIC 模式下，从 assigneeUserIds 获取
        List<Long> assigneeUserIds = node.getAssigneeUserIds();
        if (assigneeUserIds != null && !assigneeUserIds.isEmpty()) {
            return assigneeUserIds;
        }

        return Collections.emptyList();
    }

    @Transactional
    public void rollback(Long requirementId, String comment) {
        workflowRuntimeMigrationService.alignRequirementInstanceIfNeeded(requirementId);
        Long operatorId = SecurityUtils.getCurrentUserId();
        WorkflowInstance instance = getRunningInstance(requirementId);

        if (instance.getPreviousNodeId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已在开始节点，无法回退");
        }

        WorkflowNode currentNode = getNode(instance.getWorkflowVersionId(), instance.getCurrentNodeId());
        validatePermission(instance, requirementMapper.selectById(requirementId), currentNode, operatorId);
        if ("end".equals(currentNode.getNodeType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "结束节点不可回退");
        }
        if (!StringUtils.hasText(comment)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "驳回原因不能为空");
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
        approvalEvaluationService.saveOnTransition(instance, currentNode, rollbackTransition.getId(), operatorId, comment);

        Integer currentLockVersion = instance.getLockVersion() == null ? 0 : instance.getLockVersion();
        instanceMapper.update(null, new LambdaUpdateWrapper<WorkflowInstance>()
            .eq(WorkflowInstance::getId, instance.getId())
            .eq(WorkflowInstance::getLockVersion, currentLockVersion)
            .set(WorkflowInstance::getCurrentNodeId, instance.getPreviousNodeId())
            .set(WorkflowInstance::getPreviousNodeId, instance.getCurrentNodeId())
            .set(WorkflowInstance::getLockVersion, currentLockVersion + 1)
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
        workflowRuntimeMigrationService.alignRequirementInstanceIfNeeded(requirementId);
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
        if (!canCancelRequirement(requirement, instance, currentNode, operatorId)) {
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
        approvalEvaluationService.saveOnTransition(instance, currentNode, cancelTransition.getId(), operatorId, comment);

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
        workflowRuntimeMigrationService.alignRequirementInstanceIfNeeded(requirementId);
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

        WorkflowGraphContext context = runtimeLoader.loadContext(instance.getWorkflowVersionId());
        WorkflowNode currentNode = context.getNode(instance.getCurrentNodeId());
        if (currentNode == null) {
            return actions;
        }

        String currentNodeStatusCode = resolveNodeStatusCode(currentNode);
        actions.setCurrentNodeId(currentNode.getNodeId());
        actions.setCurrentNodeName(currentNode.getNodeName());
        actions.setCurrentNodeType(currentNode.getNodeType());
        actions.setCurrentNodeStatusCode(currentNodeStatusCode);
        actions.setCurrentNodeStatusName(resolveNodeStatusName(currentNodeStatusCode));
        actions.setLockVersion(instance.getLockVersion());

        Long operatorId = SecurityUtils.getCurrentUserId();
        boolean canOperate = hasOperatePermission(instance, requirement, currentNode, operatorId);
        boolean canCancel = canCancelRequirement(requirement, instance, currentNode, operatorId);

        List<AvailableTransitionDTO> transitions = Collections.emptyList();
        if (canOperate) {
            transitions = graphNavigator.resolveAvailableTargets(context, instance.getCurrentNodeId(), requirement)
                    .stream().map(targetNode -> {
                AvailableTransitionDTO dto = new AvailableTransitionDTO();
                dto.setToNodeId(targetNode.getNodeId());
                dto.setToNodeName(targetNode.getNodeName());
                dto.setLabel(targetNode.getNodeName());
                String nodeStatusCode = resolveNodeStatusCode(targetNode);
                dto.setBindStatusCode(nodeStatusCode);
                dto.setBindStatusName(resolveNodeStatusName(nodeStatusCode));
                dto.setProjectRequired(WorkflowNodeUtils.isProjectRequired(targetNode));
                return dto;
            }).collect(Collectors.toList());
        }

        actions.setTransitions(transitions);
        actions.setCanTransition(canOperate && !transitions.isEmpty());
        actions.setCanRollback(canOperate && instance.getPreviousNodeId() != null && !"end".equals(currentNode.getNodeType()));
        actions.setCanCancel(canCancel);
        actions.setEvaluationRequired(canOperate && isApprovalEvaluationRequired(currentNode));
        boolean countersignEnabled = isCountersignEnabled(currentNode);
        actions.setCountersignEnabled(countersignEnabled);
        if (countersignEnabled) {
            actions.setCanCountersign(countersignService.canCurrentUserCountersign(requirementId, currentNode.getNodeId()));
            boolean countersignComplete = countersignService.canProceedAfterCountersign(
                    instance.getId(), currentNode.getNodeId(), currentNode);
            actions.setCountersignPending(!countersignComplete);
            if (!countersignComplete) {
                actions.setCanTransition(false);
            }
        }
        boolean parallelActive = StringUtils.hasText(instance.getParallelNodeId())
                || parallelBranchService.hasPendingParallel(instance.getId());
        actions.setParallelActive(parallelActive);
        actions.setActiveParallelBranchId(instance.getActiveParallelBranchId());
        if (parallelActive) {
            actions.setParallelBranches(parallelBranchService.listByRequirementId(requirementId));
        }
        return actions;
    }

    @Transactional
    public void autoTransitionAfterCountersign(Long requirementId, String nodeId) {
        workflowRuntimeMigrationService.alignRequirementInstanceIfNeeded(requirementId);
        Requirement requirement = requirementMapper.selectById(requirementId);
        if (requirement == null) {
            return;
        }
        WorkflowInstance instance = instanceMapper.selectOne(
                new LambdaQueryWrapper<WorkflowInstance>()
                        .eq(WorkflowInstance::getRequirementId, requirementId));
        if (instance == null || !"running".equals(instance.getStatus())) {
            return;
        }
        if (!nodeId.equals(instance.getCurrentNodeId())) {
            return;
        }

        WorkflowGraphContext context = runtimeLoader.loadContext(instance.getWorkflowVersionId());
        WorkflowNode currentNode = context.getNode(nodeId);
        if (currentNode == null || !isCountersignEnabled(currentNode)) {
            return;
        }
        if (!countersignService.canProceedAfterCountersign(instance.getId(), nodeId, currentNode)) {
            return;
        }

        List<WorkflowNode> targets = graphNavigator.resolveAvailableTargets(context, nodeId, requirement);
        if (targets.isEmpty()) {
            return;
        }

        FlowTransitionRequest request = new FlowTransitionRequest();
        request.setRequirementId(requirementId);
        request.setToNodeId(targets.get(0).getNodeId());
        request.setAction("submit");
        request.setLockVersion(instance.getLockVersion());
        transition(request);
    }

    private boolean isCountersignEnabled(WorkflowNode node) {
        if (node == null || node.getProperties() == null) {
            return false;
        }
        Object enabled = node.getProperties().get("countersignEnabled");
        return Boolean.TRUE.equals(enabled);
    }

    private boolean isApprovalEvaluationRequired(WorkflowNode currentNode) {
        if (currentNode == null || !"approval".equalsIgnoreCase(currentNode.getNodeType())) {
            return false;
        }
        return !isCountersignEnabled(currentNode);
    }

    private void validateApprovalEvaluation(Integer rating, String comment) {
        if (rating == null || rating < 1 || rating > 5) {
            throw new BusinessException(400, "审批环节需选择 1-5 星评价");
        }
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

    private void validateTransition(WorkflowGraphContext context, WorkflowInstance instance, WorkflowNode currentNode,
                                     WorkflowNode targetNode, String action, Long operatorId) {
        if ("cancel".equals(action)) {
            return;
        }

        if (currentNode == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前流程节点不存在");
        }
        if (targetNode == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "目标流程节点不存在");
        }

        boolean allowed = graphNavigator.resolveAvailableTargets(context, instance.getCurrentNodeId(),
                requirementMapper.selectById(instance.getRequirementId()))
                .stream()
                .anyMatch(node -> Objects.equals(node.getNodeId(), targetNode.getNodeId()));
        if (!allowed && !"rollback".equals(action)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不允许从当前节点流转到目标节点");
        }

        validatePermission(instance, requirementMapper.selectById(instance.getRequirementId()), currentNode, operatorId);

        if (WorkflowNodeUtils.isProjectRequired(targetNode)) {
            Requirement requirement = requirementMapper.selectById(instance.getRequirementId());
            if (requirement == null || requirement.getProjectId() == null || requirement.getProjectId() <= 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "目标节点要求必须绑定项目后才能提交流转");
            }
        }
    }

    private void validatePermission(WorkflowInstance instance, Requirement requirement, WorkflowNode node, Long operatorId) {
        if (hasAdminBypassPermission()) {
            return;
        }
        if (node == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "您没有权限操作此节点");
        }
        if ("approval".equalsIgnoreCase(node.getNodeType()) && !WorkflowNodeUtils.hasValidAssignee(node)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前节点未配置处理人，请联系管理员修复流程");
        }

        String assigneeType = node.getAssigneeType();
        if (!StringUtils.hasText(assigneeType)) {
            if ("approval".equalsIgnoreCase(node.getNodeType())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "您没有权限操作此节点");
            }
            return;
        }

        // 动态权限验证：根据处理人类型检查对应权限
        switch (assigneeType) {
            case "SPECIFIED_USER":
                validateSpecifiedUserPermission(node, operatorId);
                break;
            case "SPECIFIED_ROLE":
                validateSpecifiedRolePermission(node);
                break;
            case "SPECIFIED_ROLE_GROUP":
                validateSpecifiedRoleGroupPermission(node);
                break;
            case "SPECIFIED_ORG":
                validateSpecifiedOrgPermission(node, operatorId);
                break;
            case "PREV_APPROVER":
                validatePreviousApproverPermission(instance, node, operatorId);
                break;
            case "CREATOR":
                validateCreatorPermission(requirement, operatorId);
                break;
            default:
                // 未来扩展：尝试从 properties 动态验证
                validateDynamicPermission(node, assigneeType, operatorId);
        }
    }

    private void validateSpecifiedUserPermission(WorkflowNode node, Long operatorId) {
        if (node.getAssigneeUserIds() == null || !node.getAssigneeUserIds().contains(operatorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "您没有权限操作此节点");
        }
    }

    private void validateSpecifiedRolePermission(WorkflowNode node) {
        Integer roleId = node.getAssigneeRoleId();
        if (roleId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "您没有权限操作此节点");
        }
        Role role = roleMapper.selectById(roleId.longValue());
        if (role == null || (!StringUtils.hasText(role.getCode()) && !StringUtils.hasText(role.getName()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "您没有权限操作此节点");
        }
        if (!currentUserMatchesRole(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "您没有权限操作此节点");
        }
    }

    private void validateSpecifiedRoleGroupPermission(WorkflowNode node) {
        Long roleGroupId = node.getAssigneeRoleGroupId();
        if (roleGroupId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "您没有权限操作此节点");
        }
        // 查询角色组下的所有角色
        List<Role> roles = roleMapper.selectList(
            new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleGroupId, roleGroupId)
                .eq(Role::getDeletedAt, 0)
        );
        if (roles.isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "您没有权限操作此节点");
        }
        // 检查用户是否拥有角色组中的任一角色
        boolean hasPermission = roles.stream()
            .anyMatch(this::currentUserMatchesRole);
        if (!hasPermission) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "您没有权限操作此节点");
        }
    }

    private boolean currentUserMatchesRole(Role role) {
        if (role == null) {
            return false;
        }
        List<String> userRoles = SecurityUtils.getCurrentUserRoles();
        if (userRoles.isEmpty()) {
            return false;
        }
        if (StringUtils.hasText(role.getCode()) && userRoles.contains(role.getCode().trim())) {
            return true;
        }
        return StringUtils.hasText(role.getName()) && userRoles.contains(role.getName().trim());
    }

    private void validateSpecifiedOrgPermission(WorkflowNode node, Long operatorId) {
        Long orgId = node.getAssigneeOrgId();
        if (orgId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "您没有权限操作此节点");
        }
        Set<Long> operatorOrgIds = resolveOperatorOrgIds(operatorId);
        if (operatorOrgIds.isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "您没有权限操作此节点");
        }

        String orgScopeType = String.valueOf(WorkflowNodeUtils.readProperty(node, "orgScopeType"));
        boolean includeChildren = !"current".equalsIgnoreCase(orgScopeType);
        boolean matched = includeChildren
                ? operatorOrgIds.stream().anyMatch(candidateOrgId -> isDescendantOrSelf(orgId, candidateOrgId))
                : operatorOrgIds.contains(orgId);
        if (!matched) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "您没有权限操作此节点");
        }
    }

    private void validatePreviousApproverPermission(WorkflowInstance instance, WorkflowNode node, Long operatorId) {
        if (instance == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "您没有权限操作此节点");
        }
        WorkflowInstanceTransition transition = transitionMapper.selectOne(
                new LambdaQueryWrapper<WorkflowInstanceTransition>()
                        .eq(WorkflowInstanceTransition::getInstanceId, instance.getId())
                        .eq(WorkflowInstanceTransition::getToNodeId, node.getNodeId())
                        .orderByDesc(WorkflowInstanceTransition::getId)
                        .last("LIMIT 1"));
        if (transition == null || !Objects.equals(transition.getOperatorId(), operatorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "您没有权限操作此节点");
        }
    }

    private void validateCreatorPermission(Requirement requirement, Long operatorId) {
        if (requirement == null || !Objects.equals(requirement.getCreatorId(), operatorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "您没有权限操作此节点");
        }
    }

    /**
     * 动态权限验证，支持未来扩展的处理人类型
     */
    private void validateDynamicPermission(WorkflowNode node, String assigneeType, Long operatorId) {
        // 默认拒绝未知的处理人类型
        throw new BusinessException(ErrorCode.FORBIDDEN, "未知的处理人类型: " + assigneeType);
    }

    private Set<Long> resolveOperatorOrgIds(Long operatorId) {
        LinkedHashSet<Long> orgIds = new LinkedHashSet<>();
        User user = userMapper.selectById(operatorId);
        if (user != null) {
            appendOrgId(orgIds, user.getOrgId());
            appendOrgId(orgIds, user.getDepartmentId());
            appendOrgId(orgIds, user.getRegionId());
        }
        List<UserOrganization> organizations = userOrganizationMapper.selectList(
                new LambdaQueryWrapper<UserOrganization>()
                        .eq(UserOrganization::getUserId, operatorId));
        for (UserOrganization organization : organizations) {
            appendOrgId(orgIds, organization.getOrgId());
            appendOrgId(orgIds, organization.getDepartmentId());
            appendOrgId(orgIds, organization.getRegionId());
        }
        return orgIds;
    }

    private void appendOrgId(Set<Long> orgIds, Long orgId) {
        if (orgId != null && orgId > 0) {
            orgIds.add(orgId);
        }
    }

    private boolean isDescendantOrSelf(Long ancestorOrgId, Long currentOrgId) {
        if (ancestorOrgId == null || currentOrgId == null) {
            return false;
        }
        if (Objects.equals(ancestorOrgId, currentOrgId)) {
            return true;
        }
        SysOrg currentOrg = sysOrgMapper.selectById(currentOrgId);
        return currentOrg != null
                && StringUtils.hasText(currentOrg.getPath())
                && currentOrg.getPath().contains("/" + ancestorOrgId + "/");
    }

    private boolean hasOperatePermission(WorkflowInstance instance, Requirement requirement, WorkflowNode node, Long operatorId) {
        try {
            validatePermission(instance, requirement, node, operatorId);
            return true;
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.FORBIDDEN) {
                return false;
            }
            throw ex;
        }
    }

    private BusinessException conflictException(Long instanceId) {
        WorkflowInstanceTransition latest = transitionMapper.selectOne(
                new LambdaQueryWrapper<WorkflowInstanceTransition>()
                        .eq(WorkflowInstanceTransition::getInstanceId, instanceId)
                        .isNotNull(WorkflowInstanceTransition::getCompletedAt)
                        .orderByDesc(WorkflowInstanceTransition::getCompletedAt)
                        .last("LIMIT 1"));
        Map<String, Object> data = new HashMap<>();
        if (latest != null) {
            Map<String, Object> latestTransition = new HashMap<>();
            latestTransition.put("operatorId", latest.getOperatorId());
            latestTransition.put("completedAt", latest.getCompletedAt());
            latestTransition.put("toNodeName", latest.getToNodeName());
            if (latest.getOperatorId() != null) {
                User user = userMapper.selectById(latest.getOperatorId());
                if (user != null) {
                    latestTransition.put("operatorName", user.getRealName());
                }
            }
            data.put("latestTransition", latestTransition);
        }
        return new BusinessException(409, "该需求已被他人处理，请刷新后重试", data);
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

    private boolean canCancelRequirement(Requirement requirement, WorkflowInstance instance, WorkflowNode currentNode, Long operatorId) {
        if (currentNode == null || "end".equals(currentNode.getNodeType())) {
            return false;
        }
        if (!WorkflowNodeUtils.readBooleanProperty(currentNode, "allowCancel", true)) {
            return false;
        }
        if (isCreatorOrAdmin(requirement, operatorId)) {
            return true;
        }
        return hasOperatePermission(instance, requirement, currentNode, operatorId);
    }

    private boolean isCreatorOrAdmin(Requirement requirement, Long operatorId) {
        if (requirement != null && requirement.getCreatorId() != null && requirement.getCreatorId().equals(operatorId)) {
            return true;
        }
        return hasAdminBypassPermission();
    }

    private boolean hasAdminBypassPermission() {
        return SecurityUtils.hasAnyRole("admin", "super_admin", "SUPER_ADMIN");
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
        return WorkflowNodeUtils.resolveNodeStatusCode(node, true);
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
