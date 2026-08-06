package com.demand.system.module.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.common.util.UserNameResolver;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.organization.entity.SysOrg;
import com.demand.system.module.organization.mapper.SysOrgMapper;
import com.demand.system.module.requirement.dto.RequirementAttachmentDTO;
import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.requirement.entity.RequirementHistory;
import com.demand.system.module.requirement.mapper.RequirementHistoryMapper;
import com.demand.system.module.requirement.mapper.RequirementMapper;
import com.demand.system.module.requirement.mapper.RequirementPendingTaskMapper;
import com.demand.system.module.requirement.service.RequirementApprovalEvaluationService;
import com.demand.system.module.requirement.service.RequirementPendingTaskSyncService;
import com.demand.system.module.knowledge.service.KnowledgeDocumentService;
import com.demand.system.module.knowledge.entity.KnowledgeBase;
import com.demand.system.module.knowledge.mapper.KnowledgeBaseMapper;
import com.demand.system.module.file.entity.FileRecord;
import com.demand.system.module.file.mapper.FileRecordMapper;
import com.demand.system.module.project.entity.Project;
import com.demand.system.module.project.mapper.ProjectMapper;
import com.demand.system.module.user.entity.User;
import com.demand.system.module.user.entity.UserOrganization;
import com.demand.system.module.user.mapper.UserMapper;
import com.demand.system.module.user.mapper.UserOrganizationMapper;
import com.demand.system.module.rbac.entity.Role;
import com.demand.system.module.rbac.entity.RoleGroup;
import com.demand.system.module.rbac.entity.UserRole;
import com.demand.system.module.rbac.mapper.RoleMapper;
import com.demand.system.module.rbac.mapper.RoleGroupMapper;
import com.demand.system.module.rbac.mapper.UserRoleMapper;
import com.demand.system.module.requirement.mapper.RequirementTypeMapper;
import com.demand.system.module.requirement.entity.RequirementTypeConfig;
import com.demand.system.module.workflow.engine.WorkflowDefinitionEngine;
import com.demand.system.module.workflow.engine.WorkflowVersionResolver;
import com.demand.system.module.workflow.dto.AssigneeCandidateDTO;
import com.demand.system.module.workflow.dto.ParallelBranchVO;
import com.demand.system.module.workflow.dto.AvailableTransitionDTO;
import com.demand.system.module.workflow.dto.FlowTransitionRequest;
import com.demand.system.module.workflow.dto.TransitionVO;
import com.demand.system.module.workflow.dto.WorkflowAvailableActionsDTO;
import com.demand.system.module.workflow.dto.CurrentNodeHandlerDTO;
import com.demand.system.module.workflow.dto.RatingConfigDTO;
import com.demand.system.module.workflow.engine.WorkflowGraphContext;
import com.demand.system.module.workflow.engine.WorkflowGraphNavigator;
import com.demand.system.module.workflow.engine.WorkflowRuntimeLoader;
import com.demand.system.module.workflow.entity.*;
import com.demand.system.module.workflow.mapper.WorkflowEdgeMapper;
import com.demand.system.module.workflow.mapper.WorkflowInstanceMapper;
import com.demand.system.module.workflow.mapper.WorkflowInstanceTransitionMapper;
import com.demand.system.module.workflow.mapper.WorkflowNodeMapper;
import com.demand.system.module.workflow.mapper.WorkflowVersionMapper;
import com.demand.system.module.workflow.mapper.NodeStatusMapper;
import com.demand.system.module.workflow.support.WorkflowNodeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
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

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngineService.class);

    /**
     * 工作流版本级别评价的固定评分配置（启用 + 必填 + 来源标记）
     */
    private static final Map<String, Object> VERSION_EVAL_RATING_CONFIG = Map.of(
            "enabled", true,
            "required", true,
            "source", "VERSION"
    );

    /**
     * 评价配置解析结果
     */
    record EvaluationConfig(
            boolean enabled,
            boolean required,
            String source,
            Map<String, Object> ratingConfig,
            boolean hasDimensions
    ) {
        private static final EvaluationConfig DISABLED = new EvaluationConfig(false, false, null, null, false);
        private static final EvaluationConfig VERSION_ENABLED = new EvaluationConfig(true, true, "VERSION", null, false);

        static EvaluationConfig disabled() { return DISABLED; }
        static EvaluationConfig versionEnabled() { return VERSION_ENABLED; }
    }

    private final WorkflowInstanceMapper instanceMapper;
    private final WorkflowInstanceTransitionMapper transitionMapper;
    private final WorkflowNodeMapper nodeMapper;
    private final WorkflowEdgeMapper edgeMapper;
    private final WorkflowVersionMapper workflowVersionMapper;
    private final RequirementMapper requirementMapper;
    private final RequirementHistoryMapper requirementHistoryMapper;
    private final ProjectMapper projectMapper;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final RoleGroupMapper roleGroupMapper;
    private final UserRoleMapper userRoleMapper;
    private final UserOrganizationMapper userOrganizationMapper;
    private final SysOrgMapper sysOrgMapper;
    private final NodeStatusMapper nodeStatusMapper;
    private final WorkflowGraphNavigator graphNavigator;
    private final WorkflowRuntimeLoader runtimeLoader;
    private final WorkflowNotificationService notificationService;
    private final RequirementApprovalEvaluationService approvalEvaluationService;
    private final WorkflowCountersignService countersignService;
    private final WorkflowParallelBranchService parallelBranchService;
    private final FileRecordMapper fileRecordMapper;
    private final KnowledgeDocumentService knowledgeDocumentService;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final RequirementPendingTaskSyncService pendingTaskSyncService;
    private final RequirementPendingTaskMapper pendingTaskMapper;
    private final UserNameResolver userNameResolver;
    private final WorkflowVersionResolver workflowVersionResolver;
    private final WorkflowDefinitionEngine workflowDefinitionEngine;
    private final RequirementTypeMapper requirementTypeMapper;
    private final WorkflowCcService ccService;

    public WorkflowEngineService(WorkflowInstanceMapper instanceMapper, WorkflowInstanceTransitionMapper transitionMapper,
                               WorkflowNodeMapper nodeMapper, WorkflowEdgeMapper edgeMapper,
                               WorkflowVersionMapper workflowVersionMapper,
                               RequirementMapper requirementMapper, RequirementHistoryMapper requirementHistoryMapper,
                               ProjectMapper projectMapper, UserMapper userMapper, RoleMapper roleMapper,
                               RoleGroupMapper roleGroupMapper, UserRoleMapper userRoleMapper,
                               UserOrganizationMapper userOrganizationMapper,
                               SysOrgMapper sysOrgMapper,
                               NodeStatusMapper nodeStatusMapper, WorkflowGraphNavigator graphNavigator,
                               WorkflowRuntimeLoader runtimeLoader,
                               WorkflowNotificationService notificationService,
                               RequirementApprovalEvaluationService approvalEvaluationService,
                               @Lazy WorkflowCountersignService countersignService,
                               WorkflowParallelBranchService parallelBranchService,
                               FileRecordMapper fileRecordMapper,
                               KnowledgeDocumentService knowledgeDocumentService,
                               KnowledgeBaseMapper knowledgeBaseMapper,
                               @Lazy RequirementPendingTaskSyncService pendingTaskSyncService,
                               RequirementPendingTaskMapper pendingTaskMapper,
                               UserNameResolver userNameResolver,
                               WorkflowVersionResolver workflowVersionResolver,
                               WorkflowDefinitionEngine workflowDefinitionEngine,
                               RequirementTypeMapper requirementTypeMapper,
                               WorkflowCcService ccService) {
        this.instanceMapper = instanceMapper;
        this.transitionMapper = transitionMapper;
        this.nodeMapper = nodeMapper;
        this.edgeMapper = edgeMapper;
        this.workflowVersionMapper = workflowVersionMapper;
        this.requirementMapper = requirementMapper;
        this.requirementHistoryMapper = requirementHistoryMapper;
        this.projectMapper = projectMapper;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.roleGroupMapper = roleGroupMapper;
        this.userRoleMapper = userRoleMapper;
        this.userOrganizationMapper = userOrganizationMapper;
        this.sysOrgMapper = sysOrgMapper;
        this.nodeStatusMapper = nodeStatusMapper;
        this.graphNavigator = graphNavigator;
        this.runtimeLoader = runtimeLoader;
        this.notificationService = notificationService;
        this.approvalEvaluationService = approvalEvaluationService;
        this.countersignService = countersignService;
        this.parallelBranchService = parallelBranchService;
        this.fileRecordMapper = fileRecordMapper;
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.pendingTaskSyncService = pendingTaskSyncService;
        this.pendingTaskMapper = pendingTaskMapper;
        this.userNameResolver = userNameResolver;
        this.workflowVersionResolver = workflowVersionResolver;
        this.workflowDefinitionEngine = workflowDefinitionEngine;
        this.requirementTypeMapper = requirementTypeMapper;
        this.ccService = ccService;
    }

    /**
     * 提取审批会话中的附件 ID 列表。
     * 入参是 FlowTransitionRequest.attachments（每个项至少有 fileId）。
     * 兼容空值/重复，自动去重并保持顺序。
     */
    private List<Long> extractAttachmentIds(List<RequirementAttachmentDTO> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (RequirementAttachmentDTO att : attachments) {
            if (att != null && att.getFileId() != null) {
                ids.add(att.getFileId());
            }
        }
        return new ArrayList<>(ids);
    }

    /**
     * 校验工作流版本当前是否处于启用状态（is_active=1）。
     * 工作流被管理员停用后，已存在的工作流实例不允许继续流转/驳回/取消，
     * 避免历史实例绕开停用控制继续修改状态机，破坏工作流启用/停用的边界。
     */
    private void requireWorkflowActive(WorkflowInstance instance) {
        if (instance == null || instance.getWorkflowVersionId() == null) {
            return;
        }
        WorkflowVersion version = workflowVersionMapper.selectById(instance.getWorkflowVersionId());
        if (version == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "工作流版本不存在，无法执行流转操作");
        }
        if (version.getIsActive() == null || version.getIsActive() != 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前工作流正调整中，请稍后再提交");
        }
    }

    /**
     * 只读查询工作流版本是否处于启用状态。用于 getAvailableActions 等接口
     * 在工作流停用时静默返回空 actions（前端自然不会渲染操作按钮）。
     */
    private boolean isWorkflowVersionActive(WorkflowInstance instance) {
        if (instance == null || instance.getWorkflowVersionId() == null) {
            return false;
        }
        WorkflowVersion version = workflowVersionMapper.selectById(instance.getWorkflowVersionId());
        return version != null && version.getIsActive() != null && version.getIsActive() == 1;
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
        // 节点消息提醒开关开启时，向已审批路径 / 实际处理用户推送站内消息
        notificationService.notifyApproversOnTransition(requirement, targetNode, operatorId, instance.getId());
        initCountersignIfNeeded(instance.getId(), targetNode, requirement);

        // ====== 待办任务同步（提交时无 selectedAssigneeId，按角色/角色组/组织分配）======
        pendingTaskSyncService.syncPendingTasks(requirementId, null);
        ccService.process(requirement, instance, context, startNode.getNodeId(), targetNode.getNodeId(), operatorId);
        // ====== 待办任务同步 END ======
    }

    @Transactional
    public void initWorkflow(Long requirementId, Long workflowVersionId) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "请使用 submitFromDraft 提交流程");
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
        requireWorkflowActive(instance);
        if ("completed".equals(instance.getStatus()) || "cancelled".equals(instance.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "工作流已结束，无法流转");
        }
        if (request.getLockVersion() != null && !Objects.equals(request.getLockVersion(), instance.getLockVersion())) {
            throw conflictException(instance.getId());
        }

        // 处理需求类型变更（当 newType 非空且与当前类型不同时）
        boolean typeChanged = StringUtils.hasText(request.getNewType())
                && !request.getNewType().equals(requirement.getType());
        if (typeChanged) {
            handleTypeChange(request, instance, requirement, operatorId);
            // 类型变更后重新加载需求和实例
            requirement = requirementMapper.selectById(request.getRequirementId());
            instance = instanceMapper.selectOne(
                    new LambdaQueryWrapper<WorkflowInstance>()
                            .eq(WorkflowInstance::getRequirementId, request.getRequirementId())
            );
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
        // 统一的评价配置解析（版本级别优先于节点级别）
        EvaluationConfig evalConfig = resolveEvaluationConfig(instance, currentNode);
        if (evalConfig.enabled()) {
            if ("VERSION".equals(evalConfig.source())) {
                validateVersionApprovalEvaluation(request.getRating());
            } else {
                validateApprovalEvaluation(currentNode, request.getRating(), request.getRatingDimensions(), request.getComment());
            }
        }
        // 修复 P2：按节点 properties.requireComment 校验意见必填
        if (isCommentRequired(currentNode)
                && (request.getComment() == null || request.getComment().trim().isEmpty())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前节点要求必须填写意见");
        }
        if (isAttachmentRequired(currentNode)
                && (request.getAttachments() == null || request.getAttachments().isEmpty())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前节点要求必须上传附件");
        }
        validateSelectedAssignee(targetNode, requirement, operatorId, request.getSelectedAssigneeId());

        closeCurrentTransition(instance.getId());

        // 修复：检测代审批行为。SUPER_ADMIN 不在节点 assignee 中时，action 标记为 proxy_approve
        String resolvedAction = request.getAction() != null ? request.getAction() : "submit";
        boolean isProxyApproval = isProxyApproval(currentNode, operatorId);
        if (isProxyApproval && !"cancel".equals(resolvedAction) && !"rollback".equals(resolvedAction)) {
            resolvedAction = "proxy_approve";
        }

        WorkflowInstanceTransition newTransition = new WorkflowInstanceTransition();
        newTransition.setInstanceId(instance.getId());
        newTransition.setRequirementId(request.getRequirementId());
        newTransition.setFromNodeId(instance.getCurrentNodeId());
        newTransition.setFromNodeName(currentNode != null ? currentNode.getNodeName() : "");
        newTransition.setToNodeId(request.getToNodeId());
        newTransition.setToNodeName(targetNode.getNodeName());
        newTransition.setOperatorId(operatorId);
        newTransition.setAction(resolvedAction);
        newTransition.setComment(request.getComment());
        newTransition.setStartedAt(LocalDateTime.now());
        newTransition.setAttachmentIds(extractAttachmentIds(request.getAttachments()));
        transitionMapper.insert(newTransition);

        if (evalConfig.enabled() && currentNode != null) {
            approvalEvaluationService.saveOnApprovalTransition(
                    instance, currentNode, newTransition.getId(), operatorId,
                    request.getRating(), request.getRatingDimensions(),
                    request.getComment(), request.getAttachments());
        } else {
            approvalEvaluationService.saveOnTransition(
                    instance, currentNode != null ? currentNode : targetNode, newTransition.getId(), operatorId, request.getComment(), request.getAttachments());
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
        LambdaUpdateWrapper<Requirement> requirementUpdate = new LambdaUpdateWrapper<Requirement>()
            .eq(Requirement::getId, request.getRequirementId())
            .set(Requirement::getStatus, resolveNodeStatusName(nodeStatusCode))
            .set(Requirement::getNodeStatus, nodeStatusCode);
        // 记录离开"待分析/待确认/开发中"节点的结束时间
        stampNodeEndTime(requirementUpdate, currentNode);
        requirementMapper.update(null, requirementUpdate);

        notificationService.notifyNodeEntered(requirement, targetNode, operatorId);
        // 节点消息提醒开关开启时，向已审批路径 / 实际处理用户推送站内消息
        notificationService.notifyApproversOnTransition(requirement, targetNode, operatorId, instance.getId());

        // ====== 知识库自动入库：流转附件归集到工作流绑定的知识库 ======
        autoIngestTransitionAttachments(instance, request, requirement, operatorId);
        // ====== 知识库自动入库 END ======

        instance = instanceMapper.selectById(instance.getId());
        parallelBranchService.initParallelBranchesIfNeeded(instance, context, request.getToNodeId(), requirement);
        parallelBranchService.afterTransition(instance, context, instance.getPreviousNodeId(), request.getToNodeId(), requirement);
        initCountersignIfNeeded(instance.getId(), targetNode, requirement);

        // ====== 待办任务同步 ======
        requirementMapper.update(null, new LambdaUpdateWrapper<Requirement>()
            .eq(Requirement::getId, request.getRequirementId())
            .set(Requirement::getAssigneeId, request.getSelectedAssigneeId()));
        pendingTaskSyncService.syncPendingTasks(request.getRequirementId(), request.getSelectedAssigneeId());
        WorkflowInstance latestInstance = instanceMapper.selectById(instance.getId());
        if (latestInstance != null && "running".equalsIgnoreCase(latestInstance.getStatus())) {
            ccService.process(requirement, latestInstance, context, latestInstance.getPreviousNodeId(),
                    latestInstance.getCurrentNodeId(), operatorId);
        }
        // ====== 待办任务同步 END ======
    }

    private void validateSelectedAssignee(WorkflowNode targetNode, Requirement requirement,
                                          Long operatorId, Long selectedAssigneeId) {
        List<AssigneeCandidateDTO> candidates = resolveAssigneeCandidates(targetNode, requirement, operatorId);
        if (selectedAssigneeId == null && candidates.size() > 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择一个具体处理人");
        }
        if (selectedAssigneeId == null) {
            return;
        }
        boolean matched = candidates.stream()
                .anyMatch(candidate -> Objects.equals(candidate.getId(), selectedAssigneeId));
        if (!matched) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "所选处理人不属于目标节点候选人");
        }
    }

    /**
     * 处理需求类型变更：修改需求类型并重置工作流实例为新类型绑定的工作流版本。
     * <p>流程：
     * 1. 校验当前节点是否允许修改类型（allowModifyType）
     * 2. 校验新类型是否有效且有活跃工作流版本
     * 3. 关闭当前流转记录
     * 4. 重置工作流实例到新工作流的初始节点
     * 5. 修改需求的 type/status/nodeStatus
     * 6. 记录流转历史（保留原始流转记录）
     */
    @Transactional
    protected void handleTypeChange(FlowTransitionRequest request, WorkflowInstance instance,
                                     Requirement requirement, Long operatorId) {
        WorkflowGraphContext oldContext = runtimeLoader.loadContext(instance.getWorkflowVersionId());
        WorkflowNode currentNode = oldContext.getNode(instance.getCurrentNodeId());
        if (currentNode == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前流程节点不存在");
        }

        // 1. 校验当前节点是否允许修改类型
        if (!WorkflowNodeUtils.readBooleanProperty(currentNode, "allowModifyType", false)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前节点不允许修改工单类型");
        }

        String newType = request.getNewType();
        String oldType = requirement.getType();

        // 2. 校验新类型是否已绑定活跃工作流版本
        RequirementTypeConfig newTypeConfig = requirementTypeMapper.selectByCode(newType);
        if (newTypeConfig == null || newTypeConfig.getWorkflowVersionId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "目标工单类型不存在或未绑定工作流");
        }
        if (!Boolean.TRUE.equals(newTypeConfig.getEnabled())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "目标工单类型已禁用，无法切换");
        }

        WorkflowVersion newVersion = workflowVersionMapper.selectById(newTypeConfig.getWorkflowVersionId());
        if (newVersion == null || newVersion.getIsActive() == null
                || newVersion.getIsActive() != 1 || !"active".equals(newVersion.getActivationStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "目标工单类型绑定的工作流未启用");
        }

        // 解析新工作流的初始 wait 节点（startEvent 之后的第一个 userTask）
        String initialNodeId = resolveInitialWaitNodeId(newVersion.getId());
        if (initialNodeId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "目标工单类型的工作流配置异常，缺少初始节点");
        }

        // 3. 关闭当前流转记录
        closeCurrentTransition(instance.getId());

        // 4. 记录类型变更流转记录（保留原始流转记录）
        WorkflowInstanceTransition typeChangeTransition = new WorkflowInstanceTransition();
        typeChangeTransition.setInstanceId(instance.getId());
        typeChangeTransition.setRequirementId(request.getRequirementId());
        typeChangeTransition.setFromNodeId(instance.getCurrentNodeId());
        typeChangeTransition.setFromNodeName(currentNode != null ? currentNode.getNodeName() : "");
        typeChangeTransition.setToNodeId(initialNodeId);
        typeChangeTransition.setToNodeName(resolveNodeNameById(newVersion.getId(), initialNodeId));
        typeChangeTransition.setOperatorId(operatorId);
        typeChangeTransition.setAction("type_change");
        typeChangeTransition.setComment("工单类型变更：" + oldType + " -> " + newType
                + (StringUtils.hasText(request.getComment()) ? "（" + request.getComment() + "）" : ""));
        typeChangeTransition.setStartedAt(LocalDateTime.now());
        typeChangeTransition.setAttachmentIds(extractAttachmentIds(request.getAttachments()));
        transitionMapper.insert(typeChangeTransition);

        // 5. 重置工作流实例到新版本
        Integer currentLockVersion = instance.getLockVersion() == null ? 0 : instance.getLockVersion();
        int updated = instanceMapper.update(null, new LambdaUpdateWrapper<WorkflowInstance>()
                .eq(WorkflowInstance::getId, instance.getId())
                .eq(WorkflowInstance::getLockVersion, currentLockVersion)
                .set(WorkflowInstance::getWorkflowVersionId, newVersion.getId())
                .set(WorkflowInstance::getCurrentNodeId, initialNodeId)
                .set(WorkflowInstance::getPreviousNodeId, findStartNodeId(newVersion.getId()))
                .set(WorkflowInstance::getStatus, "running")
                .set(WorkflowInstance::getLockVersion, currentLockVersion + 1)
        );
        if (updated <= 0) {
            throw conflictException(instance.getId());
        }

        // 6. 更新需求类型和状态
        String nodeStatusCode = resolveNodeStatusCodeByNodeId(newVersion.getId(), initialNodeId);
        LambdaUpdateWrapper<Requirement> reqUpdate = new LambdaUpdateWrapper<Requirement>()
                .eq(Requirement::getId, request.getRequirementId())
                .set(Requirement::getType, newType)
                .set(Requirement::getStatus, resolveNodeStatusName(nodeStatusCode))
                .set(Requirement::getNodeStatus, nodeStatusCode);
        requirementMapper.update(null, reqUpdate);

        // 记录需求历史变更
        RequirementHistory history = new RequirementHistory();
        history.setRequirementId(request.getRequirementId());
        history.setOperatorId(operatorId);
        history.setFieldName("type");
        history.setOldValue(oldType);
        history.setNewValue(newType);
        history.setCreatedAt(LocalDateTime.now());
        requirementHistoryMapper.insert(history);

        // 同步待办
        pendingTaskSyncService.syncPendingTasks(request.getRequirementId(), null);

        log.info("需求类型变更: requirementId={}, oldType={}, newType={}, newVersionId={}, initialNodeId={}",
                request.getRequirementId(), oldType, newType, newVersion.getId(), initialNodeId);
    }

    /**
     * 解析工作流版本中 startEvent 之后的第一个 wait 节点 ID
     */
    private String resolveInitialWaitNodeId(Long workflowVersionId) {
        WorkflowGraphContext context = runtimeLoader.loadContext(workflowVersionId);
        WorkflowNode startNode = context.nodesById().values().stream()
                .filter(node -> "start".equalsIgnoreCase(node.getNodeType()))
                .findFirst()
                .orElse(null);
        if (startNode == null) {
            return null;
        }
        List<WorkflowNode> targets = graphNavigator.resolveAvailableTargets(context, startNode.getNodeId(), null);
        return targets.isEmpty() ? null : targets.get(0).getNodeId();
    }

    /**
     * 查找工作流版本中的 start 节点 ID
     */
    private String findStartNodeId(Long workflowVersionId) {
        WorkflowGraphContext context = runtimeLoader.loadContext(workflowVersionId);
        return context.nodesById().values().stream()
                .filter(node -> "start".equalsIgnoreCase(node.getNodeType()))
                .map(WorkflowNode::getNodeId)
                .findFirst()
                .orElse(null);
    }

    /**
     * 根据节点 ID 解析节点状态码
     */
    private String resolveNodeStatusCodeByNodeId(Long workflowVersionId, String nodeId) {
        WorkflowGraphContext context = runtimeLoader.loadContext(workflowVersionId);
        WorkflowNode node = context.getNode(nodeId);
        if (node == null) {
            return null;
        }
        return resolveNodeStatusCode(node);
    }

    /**
     * 根据节点 ID 解析节点名称
     */
    private String resolveNodeNameById(Long workflowVersionId, String nodeId) {
        WorkflowGraphContext context = runtimeLoader.loadContext(workflowVersionId);
        WorkflowNode node = context.getNode(nodeId);
        return node != null && StringUtils.hasText(node.getNodeName()) ? node.getNodeName() : "";
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
     * 知识库自动入库：流转时如有附件，自动归集到工作流版本绑定的知识库。
     *
     * 核心逻辑：
     * 1. 从 workflowVersion.knowledgeBaseId 获取目标知识库
     * 2. 从 FlowTransitionRequest.attachments 提取附件列表
     * 3. 通过 KnowledgeDocumentService.syncRequirementAttachmentsWithContext 入库
     *    - 已有判重机制（fileName + fileSize）
     *    - 已有异步处理（RabbitMQ -> 解析 -> 分块 -> 向量化）
     *    - 已有需求引用关联（KnowledgeDocumentRequirementRef）
     */
    private void autoIngestTransitionAttachments(WorkflowInstance instance,
                                                  FlowTransitionRequest request,
                                                  Requirement requirement,
                                                  Long operatorId) {
        // 1. 检查附件是否为空
        if (request.getAttachments() == null || request.getAttachments().isEmpty()) {
            return;
        }

        // 2. 获取工作流版本，查询绑定的知识库
        WorkflowVersion workflowVersion = workflowVersionMapper.selectById(instance.getWorkflowVersionId());
        if (workflowVersion == null || workflowVersion.getKnowledgeBaseId() == null) {
            log.debug("工作流版本未绑定知识库，跳过附件自动入库，versionId={}", instance.getWorkflowVersionId());
            return;
        }

        Long knowledgeBaseId = workflowVersion.getKnowledgeBaseId();

        // 3. 校验知识库存在且可用
        KnowledgeBase kb = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (kb == null) {
            log.warn("工作流绑定的知识库不存在，跳过自动入库，versionId={}, knowledgeBaseId={}",
                    instance.getWorkflowVersionId(), knowledgeBaseId);
            return;
        }

        // 4. 构建需求上下文信息
        String requirementCode = requirement.getRequirementNo() != null ? requirement.getRequirementNo() : "REQ-" + requirement.getId();
        String requirementTitle = requirement.getTitle();

        try {
            // 5. 调用知识库文档服务，显式入库到当前工作流绑定的知识库
            knowledgeDocumentService.syncRequirementAttachmentsToKnowledgeBase(
                    knowledgeBaseId,
                    requirement.getProjectId(),
                    requirement.getId(),
                    requirementCode,
                    requirementTitle,
                    request.getAttachments(),
                    operatorId
            );
            log.info("流转附件自动入库成功，requirementId={}, knowledgeBaseId={}, attachmentCount={}",
                    requirement.getId(), knowledgeBaseId, request.getAttachments().size());
        } catch (Exception e) {
            // 知识库入库失败不影响主流程，仅记录日志
            log.error("流转附件自动入库失败，requirementId={}, knowledgeBaseId={}",
                    requirement.getId(), knowledgeBaseId, e);
        }
    }

    /**
     * 会签人占位 ID：-1 表示"需求提出人"，在运行时解析为 requirement.creatorId
     */
    private static final long COUNTERSIGN_CREATOR_PLACEHOLDER_ID = -1L;

    /**
     * 解析会签人列表
     *
     * 会签人 ID 列表中可包含占位值 -1（表示"需求提出人"），
     * 在工作流执行时会被动态解析为 requirement.creatorId。
     */
    private List<Long> resolveCountersignApprovers(WorkflowNode node, Requirement requirement) {
        Map<String, Object> properties = node.getProperties();
        if (properties == null) {
            return Collections.emptyList();
        }

        Object rawApprovers = "FIXED".equals(properties.get("countersignMode"))
                ? properties.get("countersignApprovers")
                : node.getAssigneeUserIds();

        List<Long> approverIds = normalizeApproverIds(rawApprovers);
        if (approverIds.isEmpty()) {
            return Collections.emptyList();
        }

        Long creatorId = requirement == null ? null : requirement.getCreatorId();
        LinkedHashSet<Long> resolved = new LinkedHashSet<>();
        for (Long approverId : approverIds) {
            if (approverId == COUNTERSIGN_CREATOR_PLACEHOLDER_ID) {
                if (creatorId != null) {
                    resolved.add(creatorId);
                }
                continue;
            }
            resolved.add(approverId);
        }
        return new ArrayList<>(resolved);
    }

    private List<Long> normalizeApproverIds(Object rawApprovers) {
        if (!(rawApprovers instanceof Collection<?> collection)) {
            return Collections.emptyList();
        }

        List<Long> approverIds = new ArrayList<>();
        for (Object item : collection) {
            Long id = normalizeApproverId(item);
            if (id != null) {
                approverIds.add(id);
            }
        }
        return approverIds;
    }

    private Long normalizeApproverId(Object value) {
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof Integer integerValue) {
            return integerValue.longValue();
        }
        if (value instanceof String stringValue && StringUtils.hasText(stringValue)) {
            try {
                return Long.parseLong(stringValue.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    @Transactional
    public void rollback(Long requirementId, String comment) {
        Long operatorId = SecurityUtils.getCurrentUserId();
        WorkflowInstance instance = getRunningInstance(requirementId);
        requireWorkflowActive(instance);

        WorkflowNode currentNode = getNode(instance.getWorkflowVersionId(), instance.getCurrentNodeId());
        if ("start".equals(currentNode.getNodeType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已在开始节点，无法回退");
        }
        if ("end".equals(currentNode.getNodeType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "结束节点不可回退");
        }

        // 如果 previousNodeId 为空（如迁移后），尝试从工作流图找到上一个节点
        String targetNodeId = instance.getPreviousNodeId();
        if (targetNodeId == null) {
            WorkflowGraphContext context = runtimeLoader.loadContext(instance.getWorkflowVersionId());
            WorkflowNode targetNode = graphNavigator.resolveRollbackTarget(context, instance.getCurrentNodeId(), requirementMapper.selectById(requirementId));
            if (targetNode == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "无法确定回退目标节点");
            }
            targetNodeId = targetNode.getNodeId();
        }

        validatePermission(instance, requirementMapper.selectById(requirementId), currentNode, operatorId);
        if (!StringUtils.hasText(comment)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "驳回原因不能为空");
        }

        WorkflowNode targetNode = getNode(instance.getWorkflowVersionId(), targetNodeId);

        closeCurrentTransition(instance.getId());

        WorkflowInstanceTransition rollbackTransition = new WorkflowInstanceTransition();
        rollbackTransition.setInstanceId(instance.getId());
        rollbackTransition.setRequirementId(requirementId);
        rollbackTransition.setFromNodeId(instance.getCurrentNodeId());
        rollbackTransition.setFromNodeName(currentNode.getNodeName());
        rollbackTransition.setToNodeId(targetNodeId);
        rollbackTransition.setToNodeName(targetNode != null ? targetNode.getNodeName() : "");
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
            .set(WorkflowInstance::getCurrentNodeId, targetNodeId)
            .set(WorkflowInstance::getPreviousNodeId, instance.getCurrentNodeId())
            .set(WorkflowInstance::getLockVersion, currentLockVersion + 1)
        );

        String nodeStatusCode = resolveNodeStatusCode(targetNode);
        LambdaUpdateWrapper<Requirement> rollbackUpdate = new LambdaUpdateWrapper<Requirement>()
            .eq(Requirement::getId, requirementId)
            .set(Requirement::getStatus, resolveNodeStatusName(nodeStatusCode))
            .set(Requirement::getNodeStatus, nodeStatusCode);
        // 回退：当前节点结束，记录对应时间戳
        stampNodeEndTime(rollbackUpdate, currentNode);
        requirementMapper.update(null, rollbackUpdate);
        pendingTaskSyncService.syncPendingTasks(requirementId, null);
        WorkflowGraphContext context = runtimeLoader.loadContext(instance.getWorkflowVersionId());
        WorkflowInstance latestInstance = instanceMapper.selectById(instance.getId());
        Requirement latestRequirement = requirementMapper.selectById(requirementId);
        if (latestInstance != null && latestRequirement != null && "running".equalsIgnoreCase(latestInstance.getStatus())) {
            ccService.process(latestRequirement, latestInstance, context, latestInstance.getPreviousNodeId(),
                    latestInstance.getCurrentNodeId(), operatorId);
        }
    }

    @Transactional
    public void cancel(Long requirementId, String comment) {
        Long operatorId = SecurityUtils.getCurrentUserId();
        Requirement requirement = requirementMapper.selectById(requirementId);
        if (requirement == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "需求不存在");
        }
        WorkflowInstance instance = getRunningInstance(requirementId);
        requireWorkflowActive(instance);

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

        LambdaUpdateWrapper<Requirement> cancelUpdate = new LambdaUpdateWrapper<Requirement>()
            .eq(Requirement::getId, requirementId)
            .set(Requirement::getStatus, resolveNodeStatusName("CANCELLED"))
            .set(Requirement::getNodeStatus, "CANCELLED");
        // 取消：当前节点结束，记录对应时间戳
        stampNodeEndTime(cancelUpdate, currentNode);
        requirementMapper.update(null, cancelUpdate);
        pendingTaskSyncService.syncPendingTasks(requirementId, null);
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

        // 工作流启用状态：实例存在但版本已停用时，不返回任何可用操作，前端不会渲染按钮
        boolean workflowActive = isWorkflowVersionActive(instance);
        actions.setWorkflowActive(workflowActive);
        if (!workflowActive) {
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
        // 修复 P2：返回当前节点是否必填意见，前端按此显示必填提示
        actions.setCurrentNodeRequireComment(isCommentRequired(currentNode));
        actions.setCurrentNodeRequireAttachment(isAttachmentRequired(currentNode));

        Long operatorId = SecurityUtils.getCurrentUserId();
        boolean canOperate = hasOperatePermission(instance, requirement, currentNode, operatorId);
        boolean canCancel = canCancelRequirement(requirement, instance, currentNode, operatorId);

        // 基于工作流节点权限判断编辑/删除/拆分权限
        boolean isCreatorOrAdmin = isCreatorOrAdmin(requirement, operatorId);
        actions.setCanEdit(canOperate);
        actions.setCanDelete(isCreatorOrAdmin);
        actions.setCanSplit(canOperate);

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
                dto.setAssigneeType(targetNode.getAssigneeType());
                dto.setAssigneeTypeName(resolveAssigneeTypeName(targetNode));
                List<AssigneeCandidateDTO> assigneeCandidates = resolveAssigneeCandidates(targetNode, requirement, operatorId);
                dto.setAssigneeCandidates(assigneeCandidates);
                dto.setDefaultAssigneeId(!assigneeCandidates.isEmpty() ? assigneeCandidates.get(0).getId() : null);
                dto.setAssigneeDisplayName(resolveAssigneeDisplayName(targetNode, requirement, operatorId, assigneeCandidates));
                dto.setAssigneeScopeName(resolveAssigneeScopeName(targetNode, requirement, operatorId));
                return dto;
            }).collect(Collectors.toList());
        }

        actions.setTransitions(transitions);
        actions.setCanTransition(canOperate && !transitions.isEmpty());
        actions.setCanRollback(canOperate && !"start".equals(currentNode.getNodeType()) && !"end".equals(currentNode.getNodeType()));
        actions.setCanCancel(canCancel);

        // 统一的评价配置解析（版本级别优先于节点级别）
        EvaluationConfig evalConfig = resolveEvaluationConfig(instance, currentNode);
        if ("VERSION".equals(evalConfig.source())) {
            actions.setEvaluationRequired(canOperate);
            actions.setCurrentNodeRatingConfig(VERSION_EVAL_RATING_CONFIG);
        } else if ("NODE".equals(evalConfig.source())) {
            actions.setEvaluationRequired(canOperate && evalConfig.required());
            actions.setCurrentNodeRatingConfig(evalConfig.ratingConfig());
        } else {
            actions.setEvaluationRequired(false);
        }
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

        // 节点是否允许修改需求类型
        boolean canModifyType = canOperate && WorkflowNodeUtils.readBooleanProperty(currentNode, "allowModifyType", false);
        actions.setCanModifyType(canModifyType);

        // 筛选有活跃工作流版本且与当前类型不同的类型列表
        if (canModifyType) {
            List<RequirementTypeConfig> allTypes = requirementTypeMapper.selectList(
                    new LambdaQueryWrapper<RequirementTypeConfig>()
                            .orderByAsc(RequirementTypeConfig::getSortOrder)
            );
            List<WorkflowAvailableActionsDTO.RequirementTypeOption> typeOptions = new ArrayList<>();
            for (RequirementTypeConfig config : allTypes) {
                // 跳过当前类型
                if (Objects.equals(config.getCode(), requirement.getType())) {
                    continue;
                }
                // 跳过已禁用的类型（工作流停用时联动禁用，不作为切换候选）
                if (!Boolean.TRUE.equals(config.getEnabled())) {
                    continue;
                }
                // 跳过未绑定工作流版本或绑定版本未激活的
                if (config.getWorkflowVersionId() == null) {
                    continue;
                }
                WorkflowVersion typeVersion = workflowVersionMapper.selectById(config.getWorkflowVersionId());
                if (typeVersion == null || typeVersion.getIsActive() == null
                        || typeVersion.getIsActive() != 1 || !"active".equals(typeVersion.getActivationStatus())) {
                    continue;
                }
                WorkflowAvailableActionsDTO.RequirementTypeOption option = new WorkflowAvailableActionsDTO.RequirementTypeOption();
                option.setCode(config.getCode());
                option.setName(config.getName());
                option.setColor(config.getColor());
                typeOptions.add(option);
            }
            actions.setAvailableTypes(typeOptions);
        } else {
            actions.setAvailableTypes(Collections.emptyList());
        }

        return actions;
    }

    /**
     * 批量获取需求列表页当前节点处理人信息（轻量，仅返回显示所需字段）
     *
     * <p>核心逻辑：</p>
     * <ul>
     *   <li>SPECIFIED_ROLE 且角色仅 1 人 → display = 用户姓名</li>
     *   <li>SPECIFIED_ROLE 且角色多人   → display = 角色名称</li>
     *   <li>其他类型                   → display = 候选人名称 / 类型名称 / "-"</li>
     * </ul>
     *
     * @param requirementIds 需求 ID 集合
     * @return 每条需求的当前节点处理人信息（无工作流实例的需求数据为空列表中不包含）
     */
    public List<CurrentNodeHandlerDTO> batchGetCurrentNodeHandlers(Set<Long> requirementIds) {
        if (requirementIds == null || requirementIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. 批量查询这些需求的工作流运行实例
        List<WorkflowInstance> instances = instanceMapper.selectList(
                new LambdaQueryWrapper<WorkflowInstance>()
                        .in(WorkflowInstance::getRequirementId, requirementIds)
                        .eq(WorkflowInstance::getStatus, "running")
        );
        if (instances.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 收集所有涉及的 workflowVersionId
        Set<Long> versionIds = instances.stream()
                .map(WorkflowInstance::getWorkflowVersionId)
                .collect(Collectors.toSet());

        // 3. 批量加载工作流图上下文并缓存
        Map<Long, WorkflowGraphContext> contextCache = new java.util.HashMap<>();
        for (Long vid : versionIds) {
            try {
                contextCache.put(vid, runtimeLoader.loadContext(vid));
            } catch (Exception e) {
                log.warn("加载工作流版本 {} 图上下文失败: {}", vid, e.getMessage());
            }
        }

        // 4. 批量查询需求实体（用于 CREATOR 类型的处理人解析）
        List<Requirement> requirements = requirementMapper.selectList(
                new LambdaQueryWrapper<Requirement>()
                        .in(Requirement::getId, requirementIds)
        );
        Map<Long, Requirement> requirementMap = requirements.stream()
                .collect(Collectors.toMap(Requirement::getId, r -> r));

        Long operatorId = SecurityUtils.getCurrentUserId();

        List<CurrentNodeHandlerDTO> results = new java.util.ArrayList<>();

        for (WorkflowInstance instance : instances) {
            WorkflowGraphContext ctx = contextCache.get(instance.getWorkflowVersionId());
            if (ctx == null) continue;

            WorkflowNode currentNode = ctx.getNode(instance.getCurrentNodeId());
            if (currentNode == null) continue;

            Requirement req = requirementMap.get(instance.getRequirementId());
            String assigneeType = currentNode.getAssigneeType();
            List<AssigneeCandidateDTO> candidates = resolveAssigneeCandidates(currentNode, req, operatorId);

            CurrentNodeHandlerDTO dto = new CurrentNodeHandlerDTO();
            dto.setRequirementId(instance.getRequirementId());
            dto.setCurrentNodeId(instance.getCurrentNodeId());
            dto.setCurrentNodeName(currentNode.getNodeName());
            dto.setAssigneeType(assigneeType);
            dto.setAssigneeTypeName(resolveAssigneeTypeName(currentNode));
            dto.setCandidates(candidates);
            dto.setDisplay(resolveHandlerDisplay(currentNode, candidates, assigneeType, req, operatorId));
            dto.setAssigneeScopeName(resolveAssigneeScopeName(currentNode, req, operatorId));

            results.add(dto);
        }

        return results;
    }

    /**
     * 在候选人列表中查找与指定 assigneeId 匹配的用户名
     *
     * @param candidates 候选人列表
     * @param assigneeId 指派人ID
     * @return 匹配的用户名，无匹配时返回 null
     */
    private String findMatchingCandidateName(List<AssigneeCandidateDTO> candidates, Long assigneeId) {
        if (candidates == null || assigneeId == null) {
            return null;
        }
        for (AssigneeCandidateDTO candidate : candidates) {
            if (candidate.getId() != null && candidate.getId().equals(assigneeId)) {
                return candidate.getName();
            }
        }
        return null;
    }

    /**
     * 根据当前节点配置和候选用户列表，计算负责人列的显示文本。
     * 只显示名称，不带类型前缀。
     *
     * <p>规则优先级：</p>
     * <ol>
     *   <li>SPECIFIED_ROLE + 1 个候选人 → "张三"</li>
     *   <li>SPECIFIED_ROLE + 多个候选人 → "运维需求分析员"</li>
     *   <li>有候选用户 → "张三"</li>
     *   <li>CREATOR → "张三"</li>
     *   <li>PREV_APPROVER → "上一处理人"</li>
     *   <li>其他 → "-"</li>
     * </ol>
     */
    public String resolveHandlerDisplay(WorkflowNode node,
                                          List<AssigneeCandidateDTO> candidates,
                                          String assigneeType,
                                          Requirement requirement,
                                          Long operatorId) {
        if (!StringUtils.hasText(assigneeType)) {
            return "-";
        }

        // SPECIFIED_ROLE: 核心逻辑 — 单人显示姓名，多人显示角色名
        if ("SPECIFIED_ROLE".equals(assigneeType)) {
            // 优先判断：需求已指定具体的 assignee_id 且该用户在候选人中 → 显示该用户姓名
            if (requirement != null && requirement.getAssigneeId() != null) {
                String matchedName = findMatchingCandidateName(candidates, requirement.getAssigneeId().longValue());
                if (matchedName != null) {
                    return matchedName;
                }
            }
            if (candidates != null && !candidates.isEmpty()) {
                if (candidates.size() == 1) {
                    // 仅 1 人 → 显示用户姓名
                    return candidates.get(0).getName();
                } else {
                    // 多人 → 显示角色名称
                    return resolveAssigneeDisplayName(node, requirement, operatorId, candidates);
                }
            }
            return resolveAssigneeDisplayName(node, requirement, operatorId, candidates) + "（暂无成员）";
        }

        // SPECIFIED_USER / SPECIFIED_ORG 等：有候选人时优先判断 requirement.assigneeId 是否匹配
        if (candidates != null && !candidates.isEmpty()) {
            if (requirement != null && requirement.getAssigneeId() != null) {
                String matchedName = findMatchingCandidateName(candidates, requirement.getAssigneeId().longValue());
                if (matchedName != null) {
                    return matchedName;
                }
            }
            return candidates.get(0).getName();
        }

        // 无候选人时的兜底处理
        return switch (assigneeType) {
            case "CREATOR" -> {
                String userName = userNameResolver.resolveUserName(requirement != null ? requirement.getCreatorId() : null, null);
                yield userName != null ? userName : "-";
            }
            case "PREV_APPROVER" -> "上一处理人";
            case "SPECIFIED_ORG" -> resolveAssigneeDisplayName(node, requirement, operatorId, null) + "（暂无成员）";
            case "SPECIFIED_ROLE_GROUP" -> resolveAssigneeDisplayName(node, requirement, operatorId, null) + "（暂无成员）";
            case "SPECIFIED_USER" -> "未指定用户";
            default -> "-";
        };
    }

    @Transactional
    public void autoTransitionAfterCountersign(Long requirementId, String nodeId) {
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

    /**
     * 修复：判断当前操作是否属于"代审批"。
     * 当 SUPER_ADMIN 提交审批节点但其本身不在节点 assignee 列表中时，
     * 标记为代审批（proxy_approve），便于审计追踪。
     */
    private boolean isProxyApproval(WorkflowNode node, Long operatorId) {
        if (node == null || operatorId == null || !hasAdminBypassPermission()) {
            return false;
        }
        String nodeType = node.getNodeType();
        if (!"approval".equalsIgnoreCase(nodeType) && !"wait".equalsIgnoreCase(nodeType)) {
            return false;
        }
        String assigneeType = node.getAssigneeType();
        if (!StringUtils.hasText(assigneeType)) {
            return true;
        }
        switch (assigneeType) {
            case "SPECIFIED_USER":
                return node.getAssigneeUserIds() == null
                        || !node.getAssigneeUserIds().contains(operatorId);
            case "SPECIFIED_ROLE": {
                Integer roleId = node.getAssigneeRoleId();
                if (roleId == null) {
                    return true;
                }
                Role role = roleMapper.selectById(roleId.longValue());
                return !currentUserMatchesRole(role);
            }
            case "SPECIFIED_ROLE_GROUP": {
                Long roleGroupId = node.getAssigneeRoleGroupId();
                if (roleGroupId == null) {
                    return true;
                }
                List<Role> roles = roleMapper.selectList(
                        new LambdaQueryWrapper<Role>()
                                .eq(Role::getRoleGroupId, roleGroupId)
                                .eq(Role::getDeletedAt, 0)
                );
                return roles.stream().noneMatch(this::currentUserMatchesRole);
            }
            case "SPECIFIED_ORG": {
                Long orgId = node.getAssigneeOrgId();
                if (orgId == null) {
                    return true;
                }
                return !resolveOperatorOrgIds(operatorId).contains(orgId);
            }
            case "CREATOR":
            case "PREV_APPROVER":
            default:
                return false;
        }
    }

    /**
     * 节点是否启用了评分配置（ratingConfig.enabled == true）
     */
    private boolean isApprovalEvaluationEnabled(WorkflowNode currentNode) {
        return resolveNodeRatingConfig(currentNode).enabled();
    }

    /**
     * 工作流版本是否启用了评价功能（approvalEvaluationEnabled）
     */
    private boolean isVersionApprovalEvaluationEnabled(WorkflowInstance instance) {
        if (instance == null || instance.getWorkflowVersionId() == null) {
            return false;
        }
        WorkflowVersion version = workflowVersionMapper.selectById(instance.getWorkflowVersionId());
        if (version == null) {
            return false;
        }
        return Boolean.TRUE.equals(version.getApprovalEvaluationEnabled());
    }

    /**
     * 修复 P2：判断当前节点是否要求必填审批意见。
     * 读取节点 properties.requireComment。
     */
    private boolean isCommentRequired(WorkflowNode currentNode) {
        if (currentNode == null || currentNode.getProperties() == null) {
            return false;
        }
        Object value = currentNode.getProperties().get("requireComment");
        return Boolean.TRUE.equals(value);
    }

    /**
     * 判断当前节点是否要求流转时必须上传附件。
     * 读取节点 properties.requireAttachment。
     */
    private boolean isAttachmentRequired(WorkflowNode currentNode) {
        if (currentNode == null || currentNode.getProperties() == null) {
            return false;
        }
        Object value = currentNode.getProperties().get("requireAttachment");
        return Boolean.TRUE.equals(value);
    }

    private void validateApprovalEvaluation(WorkflowNode currentNode, Integer rating,
                                            Map<String, Integer> ratingDimensions, String comment) {
        EvaluationConfig cfg = resolveNodeRatingConfig(currentNode);
        if (!cfg.enabled()) {
            return;
        }
        if (cfg.hasDimensions()) {
            boolean dimensionsEmpty = ratingDimensions == null || ratingDimensions.isEmpty();
            if (cfg.required() && dimensionsEmpty) {
                throw new BusinessException(400, "当前节点要求完成多维评价");
            }
            if (ratingDimensions != null) {
                for (Map.Entry<String, Integer> e : ratingDimensions.entrySet()) {
                    Integer s = e.getValue();
                    if (s != null && (s < 1 || s > 5)) {
                        throw new BusinessException(400, "评分必须在 1-5 星之间");
                    }
                }
            }
        } else {
            if (cfg.required() && rating == null) {
                throw new BusinessException(400, "当前节点要求完成评价");
            }
            if (rating != null && (rating < 1 || rating > 5)) {
                throw new BusinessException(400, "评分必须在 1-5 星之间");
            }
        }
    }

    /**
     * 验证工作流版本级别的评价（评分必填且需在 1-5 星范围内）
     */
    private void validateVersionApprovalEvaluation(Integer rating) {
        if (rating == null) {
            throw new BusinessException(400, "当前工作流要求完成评价（1-5星）");
        }
        approvalEvaluationService.validateRating(rating);
    }

    /**
     * 从节点属性中解析评分配置（ratingConfig），返回节点级别的评价配置
     */
    private EvaluationConfig resolveNodeRatingConfig(WorkflowNode node) {
        if (node == null || node.getProperties() == null) {
            return EvaluationConfig.disabled();
        }
        Object rawCfg = node.getProperties().get("ratingConfig");
        if (!(rawCfg instanceof Map)) {
            return EvaluationConfig.disabled();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> cfg = (Map<String, Object>) rawCfg;
        if (!Boolean.TRUE.equals(cfg.get("enabled"))) {
            return EvaluationConfig.disabled();
        }
        boolean required = Boolean.TRUE.equals(cfg.getOrDefault("required", false));
        boolean hasDimensions = cfg.get("dimensions") instanceof List && !((List<?>) cfg.get("dimensions")).isEmpty();
        return new EvaluationConfig(true, required, "NODE", cfg, hasDimensions);
    }

    /**
     * 统一的评价配置解析：版本级别优先，节点级别兜底
     */
    private EvaluationConfig resolveEvaluationConfig(WorkflowVersion version, WorkflowNode node) {
        if (version != null && Boolean.TRUE.equals(version.getApprovalEvaluationEnabled())) {
            return EvaluationConfig.versionEnabled();
        }
        return resolveNodeRatingConfig(node);
    }

    /**
     * 统一的评价配置解析（从实例查询版本）
     */
    private EvaluationConfig resolveEvaluationConfig(WorkflowInstance instance, WorkflowNode node) {
        if (instance == null || instance.getWorkflowVersionId() == null) {
            return EvaluationConfig.disabled();
        }
        WorkflowVersion version = workflowVersionMapper.selectById(instance.getWorkflowVersionId());
        return resolveEvaluationConfig(version, node);
    }

    public List<TransitionVO> getTransitionHistory(Long requirementId) {
        List<WorkflowInstanceTransition> transitions = transitionMapper.selectList(
            new LambdaQueryWrapper<WorkflowInstanceTransition>()
                .eq(WorkflowInstanceTransition::getRequirementId, requirementId)
                .orderByAsc(WorkflowInstanceTransition::getCreatedAt)
        );

        // 收集所有需要补全的字段：操作人姓名、附件元信息
        Set<Long> operatorIds = new LinkedHashSet<>();
        Set<Long> fileIds = new LinkedHashSet<>();
        for (WorkflowInstanceTransition t : transitions) {
            if (t.getOperatorId() != null) {
                operatorIds.add(t.getOperatorId());
            }
            if (t.getAttachmentIds() != null) {
                fileIds.addAll(t.getAttachmentIds());
            }
        }

        Map<Long, FileRecord> fileRecordMap = new HashMap<>();
        Set<Long> fileUploaderIds = new LinkedHashSet<>();
        if (!fileIds.isEmpty()) {
            for (FileRecord r : fileRecordMapper.selectBatchIds(fileIds)) {
                if (r != null && r.getId() != null) {
                    fileRecordMap.put(r.getId(), r);
                    if (r.getUploaderId() != null) {
                        fileUploaderIds.add(r.getUploaderId());
                    }
                }
            }
        }

        // 合并：操作人 + 文件上传人共用一次 sys_user 批量查询，结果分别归类
        Map<Long, String> operatorNameMap = new HashMap<>();
        Map<Long, String> fileUploaderNameMap = new HashMap<>();
        Set<Long> allUserIds = new LinkedHashSet<>();
        allUserIds.addAll(operatorIds);
        allUserIds.addAll(fileUploaderIds);
        if (!allUserIds.isEmpty()) {
            for (User u : userMapper.selectBatchIds(allUserIds)) {
                if (u == null) continue;
                String name = StringUtils.hasText(u.getRealName()) ? u.getRealName() : u.getUsername();
                if (operatorIds.contains(u.getId())) {
                    operatorNameMap.put(u.getId(), name);
                }
                if (fileUploaderIds.contains(u.getId())) {
                    fileUploaderNameMap.put(u.getId(), name);
                }
            }
        }

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
                vo.setOperatorName(operatorNameMap.get(t.getOperatorId()));
            }

            if (t.getAttachmentIds() != null && !t.getAttachmentIds().isEmpty()) {
                vo.setAttachmentIds(new ArrayList<>(t.getAttachmentIds()));
                List<com.demand.system.module.workflow.dto.TransitionAttachmentVO> atts = new ArrayList<>();
                for (Long fid : t.getAttachmentIds()) {
                    FileRecord r = fileRecordMap.get(fid);
                    if (r == null) {
                        continue;
                    }
                    com.demand.system.module.workflow.dto.TransitionAttachmentVO a =
                            new com.demand.system.module.workflow.dto.TransitionAttachmentVO();
                    a.setFileId(r.getId());
                    a.setName(r.getOriginalName());
                    a.setSize(r.getFileSize());
                    a.setContentType(r.getContentType());
                    a.setBucketName(r.getBucketName());
                    a.setObjectName(r.getStorageName());
                    a.setUploadedAt(r.getCreatedAt());
                    a.setUploaderId(r.getUploaderId());
                    if (r.getUploaderId() != null) {
                        a.setUploaderName(fileUploaderNameMap.get(r.getUploaderId()));
                    }
                    atts.add(a);
                }
                vo.setAttachments(atts);
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
            log.debug("Admin bypass: userId={}, requirementId={}", operatorId, requirement.getId());
            return;
        }
        if (hasRuntimePendingTask(instance, requirement)) {
            validateRuntimePendingPermission(instance, requirement.getId(), operatorId);
            return;
        }
        if (requiresRuntimePendingPermission(instance, node)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "您没有权限操作此节点");
        }
        if (node == null) {
            log.warn("Node is null for requirement: requirementId={}, userId={}", requirement.getId(), operatorId);
            throw new BusinessException(ErrorCode.FORBIDDEN, "您没有权限操作此节点");
        }
        if ("approval".equalsIgnoreCase(node.getNodeType()) && !WorkflowNodeUtils.hasValidAssignee(node)) {
            log.warn("Node has no valid assignee: requirementId={}, nodeId={}, nodeName={}",
                    requirement.getId(), node.getNodeId(), node.getNodeName());
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前节点未配置处理人，请联系管理员修复流程");
        }

        String assigneeType = node.getAssigneeType();
        if (!StringUtils.hasText(assigneeType)) {
            if ("approval".equalsIgnoreCase(node.getNodeType())) {
                log.warn("Approval node has no assigneeType: requirementId={}, nodeId={}, nodeName={}",
                        requirement.getId(), node.getNodeId(), node.getNodeName());
                throw new BusinessException(ErrorCode.FORBIDDEN, "您没有权限操作此节点");
            }
            return;
        }

        log.debug("Validating permission: userId={}, requirementId={}, nodeId={}, nodeName={}, assigneeType={}",
                 operatorId, requirement.getId(), node.getNodeId(), node.getNodeName(), assigneeType);

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

    private boolean hasRuntimePendingTask(WorkflowInstance instance, Requirement requirement) {
        if (instance == null || requirement == null || requirement.getId() == null
                || instance.getId() == null || !StringUtils.hasText(instance.getCurrentNodeId())) {
            return false;
        }
        Long count = pendingTaskMapper.countByCurrentWorkflowPosition(
                requirement.getId(), instance.getId(), instance.getCurrentNodeId());
        return count != null && count > 0;
    }

    private boolean requiresRuntimePendingPermission(WorkflowInstance instance, WorkflowNode node) {
        return instance != null
                && "running".equals(instance.getStatus())
                && node != null
                && StringUtils.hasText(node.getAssigneeType());
    }

    private void validateRuntimePendingPermission(WorkflowInstance instance, Long requirementId, Long operatorId) {
        Long count = pendingTaskMapper.countAccessibleByCurrentWorkflowPositionAndUser(
                requirementId, instance.getId(), instance.getCurrentNodeId(), operatorId);
        if (count == null || count <= 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "您没有权限操作此节点");
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
            log.warn("Node has no assigneeRoleId: nodeId={}, nodeName={}", node.getNodeId(), node.getNodeName());
            throw new BusinessException(ErrorCode.FORBIDDEN, "您没有权限操作此节点");
        }
        Role role = roleMapper.selectById(roleId.longValue());
        if (role == null || (!StringUtils.hasText(role.getCode()) && !StringUtils.hasText(role.getName()))) {
            log.warn("Role not found or invalid: nodeId={}, nodeName={}, roleId={}",
                    node.getNodeId(), node.getNodeName(), roleId);
            throw new BusinessException(ErrorCode.FORBIDDEN, "您没有权限操作此节点");
        }

        List<String> userRoles = SecurityUtils.getCurrentUserRoles();
        log.debug("Checking role permission: nodeId={}, nodeName={}, requiredRole={}/{}, userRoles={}",
                 node.getNodeId(), node.getNodeName(), role.getCode(), role.getName(), userRoles);

        if (!currentUserMatchesRole(role)) {
            log.debug("Role mismatch: nodeId={}, nodeName={}, requiredRole={}/{}, userRoles={}",
                     node.getNodeId(), node.getNodeName(), role.getCode(), role.getName(), userRoles);
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
            log.debug("Permission granted for user: userId={}, requirementId={}, nodeId={}, nodeName={}",
                     operatorId, requirement.getId(), node.getNodeId(), node.getNodeName());
            return true;
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.FORBIDDEN) {
                log.debug("Permission denied for user: userId={}, requirementId={}, nodeId={}, nodeName={}, reason={}",
                         operatorId, requirement.getId(), node.getNodeId(), node.getNodeName(), ex.getMessage());
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

    /**
     * 用户是否已归属任何组织（orgId / regionId / departmentId 任一非空即视为有组织）
     */
    private boolean hasAnyOrg(User user) {
        if (user == null) {
            return false;
        }
        return user.getOrgId() != null || user.getRegionId() != null || user.getDepartmentId() != null;
    }

    /**
     * 候选用户是否为超级管理员：通过 user_role 关联查询判断，命中 SUPER_ADMIN 即放行
     */
    private boolean isUserSuperAdmin(User user) {
        if (user == null || user.getId() == null) {
            return false;
        }
        try {
            List<UserRole> relations = userRoleMapper.selectList(
                    new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, user.getId()));
            if (relations == null || relations.isEmpty()) {
                return false;
            }
            List<Long> roleIds = relations.stream()
                    .map(UserRole::getRoleId)
                    .filter(Objects::nonNull)
                    .toList();
            if (roleIds.isEmpty()) {
                return false;
            }
            List<Role> roles = roleMapper.selectBatchIds(roleIds);
            if (roles == null) {
                return false;
            }
            return roles.stream()
                    .filter(Objects::nonNull)
                    .anyMatch(r -> "SUPER_ADMIN".equalsIgnoreCase(r.getCode())
                            || "super_admin".equalsIgnoreCase(r.getCode())
                            || "超级管理员".equals(r.getName()));
        } catch (Exception e) {
            return false;
        }
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

    /**
     * 根据离开节点的节点状态码，给需求打上对应节点结束时间戳：
     * <ul>
     *   <li>PENDING_ANALYSIS  → analysisCompletedAt</li>
     *   <li>PENDING_CONFIRM   → confirmAt</li>
     *   <li>IN_DEVELOPMENT    → developmentCompletedAt</li>
     * </ul>
     * 其它节点不写入，避免污染。
     */
    private void stampNodeEndTime(LambdaUpdateWrapper<Requirement> updateWrapper, WorkflowNode leavingNode) {
        if (leavingNode == null) {
            return;
        }
        String code = WorkflowNodeUtils.resolveNodeStatusCode(leavingNode, false);
        if (!StringUtils.hasText(code)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if ("PENDING_ANALYSIS".equals(code)) {
            updateWrapper.set(Requirement::getAnalysisCompletedAt, now);
        } else if ("PENDING_CONFIRM".equals(code)) {
            updateWrapper.set(Requirement::getConfirmAt, now);
        } else if ("IN_DEVELOPMENT".equals(code)) {
            updateWrapper.set(Requirement::getDevelopmentCompletedAt, now);
        }
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

    private String resolveAssigneeTypeName(WorkflowNode node) {
        if (node == null) {
            return "";
        }
        String assigneeType = node.getAssigneeType();
        if (!StringUtils.hasText(assigneeType)) {
            return "end".equalsIgnoreCase(node.getNodeType()) ? "结束节点" : "";
        }
        return switch (assigneeType) {
            case "SPECIFIED_USER" -> "指定用户";
            case "SPECIFIED_ROLE" -> "指定角色";
            case "SPECIFIED_ROLE_GROUP" -> "指定角色组";
            case "SPECIFIED_ORG" -> "指定组织";
            case "CREATOR" -> "提交人";
            case "PREV_APPROVER" -> "上一节点处理人";
            default -> assigneeType;
        };
    }

    private String resolveAssigneeScopeName(WorkflowNode node, Requirement requirement, Long operatorId) {
        if (node == null) {
            return "";
        }
        String assigneeType = node.getAssigneeType();
        if (!StringUtils.hasText(assigneeType)) {
            return "end".equalsIgnoreCase(node.getNodeType()) ? "结束节点" : "";
        }
        return switch (assigneeType) {
            case "SPECIFIED_USER" -> "指定用户";
            case "SPECIFIED_ROLE" -> resolveRoleName(node.getAssigneeRoleId());
            case "SPECIFIED_ROLE_GROUP" -> resolveRoleGroupName(node.getAssigneeRoleGroupId());
            case "SPECIFIED_ORG" -> "指定组织";
            case "CREATOR" -> "提交人";
            case "PREV_APPROVER" -> "上一节点处理人";
            default -> assigneeType;
        };
    }

    public List<AssigneeCandidateDTO> resolveAssigneeCandidates(WorkflowNode node, Requirement requirement, Long operatorId) {
        if (node == null) {
            return Collections.emptyList();
        }

        String assigneeType = node.getAssigneeType();
        if (!StringUtils.hasText(assigneeType)) {
            return Collections.emptyList();
        }

        return switch (assigneeType) {
            case "SPECIFIED_USER" -> buildUserCandidates(node.getAssigneeUserIds());
            case "SPECIFIED_ROLE" -> resolveUserCandidatesByRoleIds(node.getAssigneeRoleId() == null
                    ? Collections.emptySet()
                    : Set.of(node.getAssigneeRoleId().longValue()));
            case "SPECIFIED_ROLE_GROUP" -> resolveRoleGroupCandidates(node.getAssigneeRoleGroupId());
            case "SPECIFIED_ORG" -> resolveOrgCandidates(node);
            case "CREATOR" -> buildUserCandidates(requirement != null && requirement.getCreatorId() != null
                    ? List.of(requirement.getCreatorId())
                    : Collections.emptyList());
            case "PREV_APPROVER" -> buildUserCandidates(operatorId != null ? List.of(operatorId) : Collections.emptyList());
            default -> Collections.emptyList();
        };
    }

    private String resolveAssigneeDisplayName(WorkflowNode node,
                                              Requirement requirement,
                                              Long operatorId,
                                              List<AssigneeCandidateDTO> assigneeCandidates) {
        if (node == null) {
            return "-";
        }

        String assigneeType = node.getAssigneeType();
        if (!StringUtils.hasText(assigneeType)) {
            return "end".equalsIgnoreCase(node.getNodeType()) ? "流程结束" : "-";
        }

        return switch (assigneeType) {
            case "SPECIFIED_USER" -> firstAssigneeCandidateName(assigneeCandidates, "未指定用户");
            case "SPECIFIED_ROLE" -> resolveRoleName(node.getAssigneeRoleId());
            case "SPECIFIED_ROLE_GROUP" -> resolveRoleGroupName(node.getAssigneeRoleGroupId());
            case "SPECIFIED_ORG" -> resolveOrgDisplayName(node);
            case "CREATOR" -> userNameResolver.resolveUserName(requirement != null ? requirement.getCreatorId() : null, "提交人");
            case "PREV_APPROVER" -> userNameResolver.resolveUserName(operatorId, "上一节点处理人");
            default -> assigneeType;
        };
    }

    private String firstAssigneeCandidateName(List<AssigneeCandidateDTO> assigneeCandidates, String fallback) {
        if (assigneeCandidates == null || assigneeCandidates.isEmpty()) {
            return fallback;
        }
        AssigneeCandidateDTO candidate = assigneeCandidates.get(0);
        return candidate != null && StringUtils.hasText(candidate.getName()) ? candidate.getName() : fallback;
    }

    private List<AssigneeCandidateDTO> resolveRoleGroupCandidates(Long roleGroupId) {
        if (roleGroupId == null) {
            return Collections.emptyList();
        }
        List<Role> roles = roleMapper.selectList(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleGroupId, roleGroupId));
        Set<Long> roleIds = roles.stream()
                .map(Role::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return resolveUserCandidatesByRoleIds(roleIds);
    }

    private List<AssigneeCandidateDTO> resolveOrgCandidates(WorkflowNode node) {
        Long orgId = node.getAssigneeOrgId();
        if (orgId == null) {
            return Collections.emptyList();
        }

        LinkedHashSet<Long> orgIds = new LinkedHashSet<>();
        orgIds.add(orgId);
        Object scopeType = WorkflowNodeUtils.readProperty(node, "orgScopeType");
        if ("include_children".equals(scopeType)) {
            List<SysOrg> organizations = sysOrgMapper.selectList(new LambdaQueryWrapper<SysOrg>()
                    .like(SysOrg::getPath, "/" + orgId + "/"));
            for (SysOrg organization : organizations) {
                if (organization != null && organization.getId() != null) {
                    orgIds.add(organization.getId());
                }
            }
        }

        LinkedHashSet<Long> userIds = new LinkedHashSet<>();
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .and(wrapper -> wrapper
                        .in(User::getOrgId, orgIds)
                        .or()
                        .in(User::getDepartmentId, orgIds)
                        .or()
                        .in(User::getRegionId, orgIds)));
        for (User user : users) {
            if (user == null) {
                continue;
            }
            if (orgIds.contains(user.getOrgId()) || orgIds.contains(user.getDepartmentId()) || orgIds.contains(user.getRegionId())) {
                userIds.add(user.getId());
            }
        }

        List<UserOrganization> organizations = userOrganizationMapper.selectList(new LambdaQueryWrapper<UserOrganization>()
                .and(wrapper -> wrapper
                        .in(UserOrganization::getOrgId, orgIds)
                        .or()
                        .in(UserOrganization::getDepartmentId, orgIds)
                        .or()
                        .in(UserOrganization::getRegionId, orgIds)));
        for (UserOrganization organization : organizations) {
            if (organization == null || organization.getUserId() == null) {
                continue;
            }
            if (orgIds.contains(organization.getOrgId())
                    || orgIds.contains(organization.getDepartmentId())
                    || orgIds.contains(organization.getRegionId())) {
                userIds.add(organization.getUserId());
            }
        }

        return buildUserCandidates(userIds);
    }

    private List<AssigneeCandidateDTO> resolveUserCandidatesByRoleIds(Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<UserRole> userRoles = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                .in(UserRole::getRoleId, roleIds));
        LinkedHashSet<Long> userIds = userRoles.stream()
                .map(UserRole::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return buildUserCandidates(userIds);
    }

    private List<AssigneeCandidateDTO> buildUserCandidates(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }

        LinkedHashSet<Long> normalizedIds = userIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalizedIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<User> users = userMapper.selectBatchIds(normalizedIds);
        Map<Long, User> userMap = users == null ? Collections.emptyMap() : users.stream()
                .filter(Objects::nonNull)
                .filter(user -> user.getId() != null)
                .collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left));

        List<AssigneeCandidateDTO> candidates = new ArrayList<>();
        for (Long userId : normalizedIds) {
            User user = userMap.get(userId);
            if (user == null) {
                continue;
            }
            // 排除无组织用户：orgId/regionId/departmentId 全为 null 的非超级管理员不出现在候选人中
            if (!hasAnyOrg(user) && !isUserSuperAdmin(user)) {
                continue;
            }
            AssigneeCandidateDTO candidate = new AssigneeCandidateDTO();
            candidate.setId(userId);
            candidate.setName(resolveUserDisplayName(user, userId));
            candidates.add(candidate);
        }
        return candidates;
    }

    private String resolveUserDisplayName(User user, Long userId) {
        if (user != null) {
            if (StringUtils.hasText(user.getRealName())) {
                return user.getRealName().trim();
            }
            if (StringUtils.hasText(user.getUsername())) {
                return user.getUsername().trim();
            }
        }
        return userId == null ? "未知用户" : "用户#" + userId;
    }

    private String resolveRoleName(Integer roleId) {
        if (roleId == null) {
            return "未指定角色";
        }
        Role role = roleMapper.selectById(Long.valueOf(roleId.longValue()));
        if (role == null) {
            return "未指定角色";
        }
        if (StringUtils.hasText(role.getName())) {
            return role.getName().trim();
        }
        if (StringUtils.hasText(role.getCode())) {
            return role.getCode().trim();
        }
        return "角色#" + roleId;
    }

    private String resolveRoleGroupName(Long roleGroupId) {
        if (roleGroupId == null) {
            return "未指定角色组";
        }
        RoleGroup roleGroup = roleGroupMapper.selectById(roleGroupId);
        if (roleGroup == null || !StringUtils.hasText(roleGroup.getName())) {
            return "角色组#" + roleGroupId;
        }
        return roleGroup.getName().trim();
    }

    private String resolveOrgDisplayName(WorkflowNode node) {
        Long orgId = node.getAssigneeOrgId();
        if (orgId == null) {
            return "未指定组织";
        }
        SysOrg org = sysOrgMapper.selectById(orgId);
        String orgName = org != null && StringUtils.hasText(org.getName()) ? org.getName().trim() : "组织#" + orgId;
        Object scopeType = WorkflowNodeUtils.readProperty(node, "orgScopeType");
        if ("include_children".equals(scopeType)) {
            return orgName + "（当前层级及子层级）";
        }
        return orgName + "（仅当前层级）";
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
