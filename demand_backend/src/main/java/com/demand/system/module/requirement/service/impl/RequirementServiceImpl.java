package com.demand.system.module.requirement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.common.result.PageResult;
import com.demand.system.common.result.Result;
import com.demand.system.common.service.DistributedIdGenerator;
import com.demand.system.module.requirement.dto.RequirementCreateDTO;
import com.demand.system.module.requirement.dto.RequirementAttachmentDTO;
import com.demand.system.module.requirement.dto.RequirementApprovalEvaluationVO;
import com.demand.system.module.requirement.dto.RequirementCommentCreateDTO;
import com.demand.system.module.requirement.dto.RequirementCommentVO;
import com.demand.system.module.requirement.dto.RequirementDraftCreateDTO;
import com.demand.system.module.requirement.dto.RequirementDraftUpdateDTO;
import com.demand.system.module.requirement.dto.RequirementMyListQueryDTO;
import com.demand.system.module.requirement.dto.RequirementSubmitDTO;
import com.demand.system.module.requirement.dto.NextNodeOptionDTO;
import com.demand.system.module.requirement.dto.RequirementDetailVO;
import com.demand.system.module.requirement.dto.RequirementQueryDTO;
import com.demand.system.module.requirement.dto.RequirementUpdateDTO;
import com.demand.system.module.requirement.dto.RequirementVO;
import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.requirement.entity.RequirementComment;
import com.demand.system.module.requirement.entity.RequirementFollow;
import com.demand.system.module.requirement.entity.RequirementHistory;
import com.demand.system.module.requirement.entity.RequirementTypeConfig;
import com.demand.system.module.requirement.mapper.CustomFieldValueMapper;
import com.demand.system.module.requirement.mapper.RequirementCommentMapper;
import com.demand.system.module.requirement.mapper.RequirementFollowMapper;
import com.demand.system.module.requirement.mapper.RequirementHistoryMapper;
import com.demand.system.module.requirement.mapper.RequirementMapper;
import com.demand.system.module.file.entity.FileRecord;
import com.demand.system.module.file.mapper.FileRecordMapper;
import com.demand.system.module.requirement.service.RequirementApprovalEvaluationService;
import com.demand.system.module.requirement.service.RequirementConfigService;
import com.demand.system.module.requirement.service.RequirementService;
import com.demand.system.module.user.entity.User;
import com.demand.system.module.user.entity.UserOrganization;
import com.demand.system.module.user.mapper.UserMapper;
import com.demand.system.module.user.mapper.UserOrganizationMapper;
import com.demand.system.module.organization.dto.SysOrgVO;
import com.demand.system.module.organization.service.SysOrgService;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.notification.service.NotificationService;
import com.demand.system.module.relation.service.RelationService;
import com.demand.system.module.relation.dto.RelationVO;
import com.demand.system.module.project.entity.Project;
import com.demand.system.module.project.mapper.ProjectMapper;
import com.demand.system.module.knowledge.service.KnowledgeDocumentService;
import com.demand.system.module.rbac.entity.Role;
import com.demand.system.module.rbac.entity.RoleGroup;
import com.demand.system.module.rbac.mapper.RoleGroupMapper;
import com.demand.system.module.rbac.mapper.RoleMapper;
import com.demand.system.module.rbac.support.RbacConstants;
import com.demand.system.module.workflow.entity.WorkflowEdge;
import com.demand.system.module.workflow.entity.WorkflowInstance;
import com.demand.system.module.workflow.entity.WorkflowNode;
import com.demand.system.module.workflow.entity.WorkflowVersion;
import com.demand.system.module.workflow.engine.WorkflowDefinitionEngine;
import com.demand.system.module.workflow.engine.WorkflowGraphContext;
import com.demand.system.module.workflow.engine.WorkflowGraphNavigator;
import com.demand.system.module.workflow.engine.WorkflowRuntimeLoader;
import com.demand.system.module.workflow.engine.WorkflowVersionResolver;
import com.demand.system.module.workflow.entity.WorkflowInstanceTransition;
import com.demand.system.module.workflow.mapper.WorkflowInstanceTransitionMapper;
import com.demand.system.module.workflow.support.WorkflowNodeUtils;
import com.demand.system.module.workflow.entity.NodeStatus;
import com.demand.system.module.workflow.mapper.WorkflowEdgeMapper;
import com.demand.system.module.workflow.mapper.WorkflowInstanceMapper;
import com.demand.system.module.workflow.mapper.NodeStatusMapper;
import com.demand.system.module.workflow.mapper.WorkflowNodeMapper;
import com.demand.system.module.workflow.mapper.WorkflowVersionMapper;
import com.demand.system.module.workflow.service.WorkflowEngineService;
import com.demand.system.module.workflow.service.WorkflowService;
import com.demand.system.module.workflow.service.WorkflowRuntimeMigrationService;
import com.demand.system.module.workflow.dto.WorkflowAvailableActionsDTO;
import com.demand.system.module.workflow.mapper.WorkflowTransitionRecordMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RequirementServiceImpl implements RequirementService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RequirementServiceImpl.class);

    private static final String REQUIREMENT_NO_PREFIX = "BR";
    private static final int REQUIREMENT_NO_MIN_SEQUENCE_WIDTH = 3;
    private static final int REQUIREMENT_NO_MAX_RETRY = 5;
    private static final DateTimeFormatter REQUIREMENT_NO_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter REQUIREMENT_NO_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 是否启用待办任务物化表优化
     * true: 使用 requirement_pending_tasks 表（推荐，性能提升80%+）
     * false: 使用原始复杂查询（兼容模式）
     */
    private static final boolean USE_PENDING_TASK_OPTIMIZATION = false;

    /**
     * 架构重构开关：是否使用V2查询（workflow_node_assignees关联表）
     * true: 使用V2架构（消除JSON_CONTAINS，性能提升100倍+）
     * false: 使用旧查询（JSON_CONTAINS + 复杂子查询）
     */
    private static final boolean USE_V2_ARCHITECTURE = true;

    private final RequirementMapper requirementMapper;
    private final RequirementFollowMapper requirementFollowMapper;
    private final RequirementHistoryMapper historyMapper;
    private final RequirementCommentMapper requirementCommentMapper;
    private final CustomFieldValueMapper customFieldValueMapper;
    private final UserMapper userMapper;
    private final UserOrganizationMapper userOrganizationMapper;
    private final SysOrgService sysOrgService;
    private final NotificationService notificationService;
    private final RelationService relationService;
    private final WorkflowService workflowService;
    private final WorkflowEngineService workflowEngineService;
    private final WorkflowVersionMapper workflowVersionMapper;
    private final WorkflowVersionResolver workflowVersionResolver;
    private final WorkflowGraphNavigator workflowGraphNavigator;
    private final WorkflowRuntimeLoader workflowRuntimeLoader;
    private final WorkflowRuntimeMigrationService workflowRuntimeMigrationService;
    private final WorkflowNodeMapper workflowNodeMapper;
    private final WorkflowEdgeMapper workflowEdgeMapper;
    private final WorkflowInstanceMapper workflowInstanceMapper;
    private final WorkflowInstanceTransitionMapper workflowInstanceTransitionMapper;
    private final WorkflowTransitionRecordMapper workflowTransitionRecordMapper;
    private final WorkflowDefinitionEngine workflowDefinitionEngine;
    private final RequirementApprovalEvaluationService approvalEvaluationService;
    private final RequirementConfigService requirementConfigService;
    private final KnowledgeDocumentService knowledgeDocumentService;
    private final NodeStatusMapper nodeStatusMapper;
    private final ProjectMapper projectMapper;
    private final RoleMapper roleMapper;
    private final RoleGroupMapper roleGroupMapper;
    private final FileRecordMapper fileRecordMapper;
    private final ObjectMapper objectMapper;
    private final DistributedIdGenerator distributedIdGenerator;
    private final com.demand.system.module.organization.service.OrgHierarchyCache orgHierarchyCache;

    public RequirementServiceImpl(RequirementMapper requirementMapper, RequirementFollowMapper requirementFollowMapper, RequirementHistoryMapper historyMapper, RequirementCommentMapper requirementCommentMapper, CustomFieldValueMapper customFieldValueMapper, UserMapper userMapper, UserOrganizationMapper userOrganizationMapper, SysOrgService sysOrgService, NotificationService notificationService, RelationService relationService, WorkflowService workflowService, WorkflowEngineService workflowEngineService, WorkflowVersionMapper workflowVersionMapper, WorkflowVersionResolver workflowVersionResolver, WorkflowGraphNavigator workflowGraphNavigator, WorkflowRuntimeLoader workflowRuntimeLoader, WorkflowRuntimeMigrationService workflowRuntimeMigrationService, WorkflowNodeMapper workflowNodeMapper, WorkflowEdgeMapper workflowEdgeMapper, WorkflowInstanceMapper workflowInstanceMapper, WorkflowInstanceTransitionMapper workflowInstanceTransitionMapper, WorkflowTransitionRecordMapper workflowTransitionRecordMapper, WorkflowDefinitionEngine workflowDefinitionEngine, RequirementApprovalEvaluationService approvalEvaluationService, RequirementConfigService requirementConfigService, KnowledgeDocumentService knowledgeDocumentService, NodeStatusMapper nodeStatusMapper, ProjectMapper projectMapper, RoleMapper roleMapper, RoleGroupMapper roleGroupMapper, FileRecordMapper fileRecordMapper, ObjectMapper objectMapper, DistributedIdGenerator distributedIdGenerator, com.demand.system.module.organization.service.OrgHierarchyCache orgHierarchyCache) {
        this.requirementMapper = requirementMapper;
        this.requirementFollowMapper = requirementFollowMapper;
        this.historyMapper = historyMapper;
        this.requirementCommentMapper = requirementCommentMapper;
        this.customFieldValueMapper = customFieldValueMapper;
        this.userMapper = userMapper;
        this.userOrganizationMapper = userOrganizationMapper;
        this.sysOrgService = sysOrgService;
        this.notificationService = notificationService;
        this.relationService = relationService;
        this.workflowService = workflowService;
        this.workflowEngineService = workflowEngineService;
        this.workflowVersionMapper = workflowVersionMapper;
        this.workflowVersionResolver = workflowVersionResolver;
        this.workflowGraphNavigator = workflowGraphNavigator;
        this.workflowRuntimeLoader = workflowRuntimeLoader;
        this.workflowRuntimeMigrationService = workflowRuntimeMigrationService;
        this.workflowNodeMapper = workflowNodeMapper;
        this.workflowEdgeMapper = workflowEdgeMapper;
        this.workflowInstanceMapper = workflowInstanceMapper;
        this.workflowInstanceTransitionMapper = workflowInstanceTransitionMapper;
        this.workflowTransitionRecordMapper = workflowTransitionRecordMapper;
        this.workflowDefinitionEngine = workflowDefinitionEngine;
        this.approvalEvaluationService = approvalEvaluationService;
        this.requirementConfigService = requirementConfigService;
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.nodeStatusMapper = nodeStatusMapper;
        this.projectMapper = projectMapper;
        this.roleMapper = roleMapper;
        this.roleGroupMapper = roleGroupMapper;
        this.fileRecordMapper = fileRecordMapper;
        this.objectMapper = objectMapper;
        this.distributedIdGenerator = distributedIdGenerator;
        this.orgHierarchyCache = orgHierarchyCache;
    }

    @Override
    public PageResult<RequirementVO> list(RequirementQueryDTO query) {
        Page<Requirement> page = new Page<>(query.getPageNum(), query.getPageSize());

        LambdaQueryWrapper<Requirement> wrapper = new LambdaQueryWrapper<>();

        // 草稿仅出现在草稿箱，全部需求中排除草稿
        wrapper.eq(Requirement::getIsDraft, false);

        Long currentUserId = SecurityUtils.getCurrentUserId();
        List<String> currentRoleCodes = SecurityUtils.getCurrentUserRoles();
        boolean isSuperAdmin = isSuperAdmin(currentRoleCodes);
        if (!isSuperAdmin) {
            List<Long> visibleOrgIds = resolveVisibleOrgIds(currentUserId, false);
            if (visibleOrgIds.isEmpty()) {
                // 方案A：用户未关联任何组织时，提示完善信息而非降级为只看自己创建的
                throw new BusinessException(400, "您尚未关联组织，请联系管理员配置您的组织信息后再查看需求");
            } else {
                wrapper.in(Requirement::getOrgId, visibleOrgIds);
            }
        }

        if (query.getProjectId() != null) {
            wrapper.eq(Requirement::getProjectId, query.getProjectId());
        }
        if (query.getParentId() != null) {
            wrapper.eq(Requirement::getParentId, query.getParentId());
        }
        if (StringUtils.hasText(query.getType())) {
            wrapper.eq(Requirement::getType, query.getType());
        }
        if (StringUtils.hasText(query.getPriority())) {
            wrapper.eq(Requirement::getPriority, query.getPriority());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(Requirement::getStatus, query.getStatus());
        }
        if (query.getAssigneeId() != null) {
            wrapper.eq(Requirement::getAssigneeId, query.getAssigneeId());
        }
        if (query.getIterationId() != null) {
            wrapper.eq(Requirement::getIterationId, query.getIterationId());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(Requirement::getTitle, query.getKeyword())
                    .or().like(Requirement::getDescription, query.getKeyword()));
        }

        if (query.getCreatedAtStart() != null) {
            wrapper.ge(Requirement::getCreatedAt, query.getCreatedAtStart());
        }
        if (query.getCreatedAtEnd() != null) {
            wrapper.le(Requirement::getCreatedAt, query.getCreatedAtEnd());
        }
        if (query.getAnalysisCompletedAtStart() != null) {
            wrapper.ge(Requirement::getAnalysisCompletedAt, query.getAnalysisCompletedAtStart());
        }
        if (query.getAnalysisCompletedAtEnd() != null) {
            wrapper.le(Requirement::getAnalysisCompletedAt, query.getAnalysisCompletedAtEnd());
        }
        if (query.getConfirmAtStart() != null) {
            wrapper.ge(Requirement::getConfirmAt, query.getConfirmAtStart());
        }
        if (query.getConfirmAtEnd() != null) {
            wrapper.le(Requirement::getConfirmAt, query.getConfirmAtEnd());
        }
        if (query.getDevelopmentCompletedAtStart() != null) {
            wrapper.ge(Requirement::getDevelopmentCompletedAt, query.getDevelopmentCompletedAtStart());
        }
        if (query.getDevelopmentCompletedAtEnd() != null) {
            wrapper.le(Requirement::getDevelopmentCompletedAt, query.getDevelopmentCompletedAtEnd());
        }

        if (StringUtils.hasText(query.getSortField()) && StringUtils.hasText(query.getSortOrder())) {
            if ("asc".equalsIgnoreCase(query.getSortOrder())) {
                wrapper.orderByAsc(getColumnFunction(query.getSortField()));
            } else {
                wrapper.orderByDesc(getColumnFunction(query.getSortField()));
            }
        } else {
            wrapper.orderByDesc(Requirement::getCreatedAt);
        }

        Page<Requirement> resultPage = requirementMapper.selectPage(page, wrapper);

        List<Requirement> records = resultPage.getRecords();
        List<RequirementVO> voList = new ArrayList<>();
        if (!records.isEmpty()) {
            // 一次性收集整页附件 fileIds 并批量回填，避免 2N 次查询
            batchEnrichAttachmentMeta(records);
            for (Requirement r : records) {
                voList.add(toRequirementVO(r, currentUserId, false, true)); // skipFollowed=true
            }
            // 批量填充关注状态
            batchFillFollowed(voList, currentUserId);
        }

        return new PageResult<>(voList, resultPage.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public RequirementVO getDetail(Long id) {
        Requirement r = requirementMapper.selectById(id);
        if (r == null) {
            throw new BusinessException("需求不存在");
        }
        requireViewPermission(r, "无权查看该需求");
        RequirementVO vo = new RequirementVO();
        BeanUtils.copyProperties(r, vo);
        fillUserNames(vo, r);
        // 合并：原本 enrichAttachmentMeta + fillTransitionAttachments 各跑 2~3 次查询，
        // 改为统一收集 fileIds/userIds 后单次批量回填，DB roundtrip 5→2。
        enrichAttachmentsAndTransitions(vo, r.getId());
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId != null) {
            fillPermissionFields(vo, r, userId);
            fillFollowed(vo, r.getId(), userId);
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(RequirementCreateDTO dto, Long creatorId) {
        Long projectId = normalizeProjectId(dto.getProjectId());
        if (workflowDefinitionEngine.hasActiveDefinition(projectId)) {
            throw new BusinessException(410, "请使用草稿创建并提交流程：POST /api/v1/requirements/drafts");
        }
        requireProjectSelection(dto.getProjectId());
        Requirement requirement = new Requirement();
        BeanUtils.copyProperties(dto, requirement);
        requirement.setProjectId(projectId);
        ensureProjectCanBeBound(requirement.getProjectId());
        RequirementTypeConfig defaultType = requirementConfigService.getDefaultType();
        if (defaultType == null || !StringUtils.hasText(defaultType.getCode())) {
            throw new BusinessException("请先配置至少一个需求类型");
        }
        requirement.setType(defaultType.getCode());
        requirement.setIterationId(null);
        requirement.setCreatorId(creatorId);
        requirement.setStatus(workflowService.resolveInitialStateName(projectId, requirement));
        requirement.setLegacyWorkflow(true);
        if (requirement.getOrderNum() == null) {
            requirement.setOrderNum(0);
        }
        requirement.setVersion(0);

        insertRequirementWithGeneratedNo(requirement);

        if (dto.getParentId() != null) {
            Requirement parent = requirementMapper.selectById(dto.getParentId());
            if (parent != null && dto.getPriority() == null && parent.getPriority() != null) {
                UpdateWrapper<Requirement> priorityWrapper = new UpdateWrapper<>();
                priorityWrapper.eq("id", requirement.getId()).set("priority", parent.getPriority());
                requirementMapper.update(null, priorityWrapper);
                recordHistory(requirement.getId(), creatorId, "priority", null, "继承父需求优先级: " + parent.getPriority());
            }
        }

        recordHistory(requirement.getId(), creatorId, "create", null, "需求创建");
        knowledgeDocumentService.syncRequirementAttachmentsWithContext(
                requirement.getProjectId(),
                requirement.getId(),
                requirement.getRequirementNo(),
                requirement.getTitle(),
                dto.getAttachments(),
                creatorId
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(RequirementUpdateDTO dto, Long userId) {
        Requirement existing = requirementMapper.selectById(dto.getId());
        if (existing == null) {
            throw new BusinessException("需求不存在");
        }

        Long operatorId = userId;
        UpdateWrapper<Requirement> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", dto.getId());

        if (dto.getTitle() != null && !Objects.equals(existing.getTitle(), dto.getTitle())) {
            recordHistory(dto.getId(), operatorId, "title", existing.getTitle(), dto.getTitle());
            updateWrapper.set("title", dto.getTitle());
        }
        if (dto.getDescription() != null && !Objects.equals(existing.getDescription(), dto.getDescription())) {
            recordHistory(dto.getId(), operatorId, "description", existing.getDescription(), dto.getDescription());
            updateWrapper.set("description", dto.getDescription());
        }
        if (dto.getType() != null && !Objects.equals(existing.getType(), dto.getType())) {
            recordHistory(dto.getId(), operatorId, "type", existing.getType(), dto.getType());
            updateWrapper.set("type", dto.getType());
        }
        if (dto.getPriority() != null && !Objects.equals(existing.getPriority(), dto.getPriority())) {
            recordHistory(dto.getId(), operatorId, "priority", existing.getPriority(), dto.getPriority());
            updateWrapper.set("priority", dto.getPriority());
        }
        if (dto.getAssigneeId() != null && !Objects.equals(existing.getAssigneeId(), dto.getAssigneeId())) {
            recordHistory(dto.getId(), operatorId, "assigneeId",
                    strVal(existing.getAssigneeId()), strVal(dto.getAssigneeId()));
            updateWrapper.set("assignee_id", dto.getAssigneeId());
        }
        if (dto.getCcUserIds() != null && !Objects.equals(existing.getCcUserIds(), dto.getCcUserIds())) {
            recordHistory(dto.getId(), operatorId, "ccUserIds", null, "更新抄送人");
            updateWrapper.set("cc_user_ids", writeJson(dto.getCcUserIds()));
        }
        if (dto.getStartDate() != null && !Objects.equals(existing.getStartDate(), dto.getStartDate())) {
            recordHistory(dto.getId(), operatorId, "startDate",
                    strDate(existing.getStartDate()), strDate(dto.getStartDate()));
            updateWrapper.set("start_date", dto.getStartDate());
        }
        if (dto.getIterationId() != null && !Objects.equals(existing.getIterationId(), dto.getIterationId())) {
            recordHistory(dto.getId(), operatorId, "iterationId",
                    strVal(existing.getIterationId()), strVal(dto.getIterationId()));
            updateWrapper.set("iteration_id", dto.getIterationId());
        }
        if (dto.getModuleId() != null && !Objects.equals(existing.getModuleId(), dto.getModuleId())) {
            recordHistory(dto.getId(), operatorId, "moduleId",
                    strVal(existing.getModuleId()), strVal(dto.getModuleId()));
            updateWrapper.set("module_id", dto.getModuleId());
        }
        if (Boolean.TRUE.equals(existing.getIsDraft()) && dto.getDueDate() != null && !Objects.equals(existing.getDueDate(), dto.getDueDate())) {
            recordHistory(dto.getId(), operatorId, "dueDate",
                    strDate(existing.getDueDate()), strDate(dto.getDueDate()));
            updateWrapper.set("due_date", dto.getDueDate());
        }
        if (dto.getEstimatedHours() != null && !Objects.equals(existing.getEstimatedHours(), dto.getEstimatedHours())) {
            recordHistory(dto.getId(), operatorId, "estimatedHours",
                    strDecimal(existing.getEstimatedHours()), strDecimal(dto.getEstimatedHours()));
            updateWrapper.set("estimated_hours", dto.getEstimatedHours());
        }
        if (dto.getActualHours() != null && !Objects.equals(existing.getActualHours(), dto.getActualHours())) {
            recordHistory(dto.getId(), operatorId, "actualHours",
                    strDecimal(existing.getActualHours()), strDecimal(dto.getActualHours()));
            updateWrapper.set("actual_hours", dto.getActualHours());
        }
        if (dto.getAttachments() != null && !Objects.equals(existing.getAttachments(), dto.getAttachments())) {
            recordHistory(dto.getId(), operatorId, "attachments", null, "更新附件");
            updateWrapper.set("attachments", writeJson(dto.getAttachments()));
        }
        if (dto.getStatus() != null && !Objects.equals(existing.getStatus(), dto.getStatus())) {
            throw new BusinessException("状态流转请使用工作流操作");
        }
        if (dto.getOrderNum() != null && !Objects.equals(existing.getOrderNum(), dto.getOrderNum())) {
            recordHistory(dto.getId(), operatorId, "orderNum",
                    strVal(existing.getOrderNum()), strVal(dto.getOrderNum()));
            updateWrapper.set("order_num", dto.getOrderNum());
        }

        if (updateWrapper.getSqlSet() != null && !updateWrapper.getSqlSet().isEmpty()) {
            requirementMapper.update(null, updateWrapper);
        }
        if (dto.getAttachments() != null) {
            knowledgeDocumentService.syncRequirementAttachmentsWithContext(
                    existing.getProjectId(),
                    existing.getId(),
                    existing.getRequirementNo(),
                    existing.getTitle(),
                    dto.getAttachments(),
                    userId
            );
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDraft(RequirementDraftCreateDTO dto, Long creatorId) {
        if (!StringUtils.hasText(dto.getTitle())) {
            throw new BusinessException(400, "请输入需求标题");
        }
        if (!StringUtils.hasText(dto.getPriority())) {
            throw new BusinessException(400, "请选择优先级");
        }
        requireProjectSelection(dto.getProjectId());

        Requirement requirement = new Requirement();
        BeanUtils.copyProperties(dto, requirement);
        requirement.setProjectId(normalizeProjectId(dto.getProjectId()));
        ensureProjectCanBeBound(requirement.getProjectId());

        // 如果前端未传递type，使用默认类型；否则使用前端传递的值
        if (!StringUtils.hasText(requirement.getType())) {
            RequirementTypeConfig defaultType = requirementConfigService.getDefaultType();
            if (defaultType == null || !StringUtils.hasText(defaultType.getCode())) {
                throw new BusinessException("请先配置至少一个需求类型");
            }
            requirement.setType(defaultType.getCode());
        }
        requirement.setIterationId(null);
        requirement.setCreatorId(creatorId);
        requirement.setStatus(resolveNodeStatusName("DRAFT"));
        requirement.setWorkflowInstanceId(null);
        requirement.setNodeStatus("DRAFT");
        requirement.setIsDraft(true);
        requirement.setLastSavedAt(LocalDateTime.now());
        requirement.setCreatorRoleCodes(SecurityUtils.getCurrentUserRoles());

        User creator = userMapper.selectById(creatorId);
        if (creator != null) {
            requirement.setDepartmentId(creator.getDepartmentId());
            requirement.setOrgId(creator.getOrgId());
        }

        if (requirement.getOrderNum() == null) {
            requirement.setOrderNum(0);
        }
        requirement.setVersion(0);

        insertRequirementWithGeneratedNo(requirement);
        recordHistory(requirement.getId(), creatorId, "create", null, "保存草稿");
        knowledgeDocumentService.syncRequirementAttachmentsWithContext(
                requirement.getProjectId(),
                requirement.getId(),
                requirement.getRequirementNo(),
                requirement.getTitle(),
                dto.getAttachments(),
                creatorId
        );
        return requirement.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDraft(RequirementDraftUpdateDTO dto, Long userId) {
        if (dto.getId() == null) {
            throw new BusinessException(400, "缺少草稿ID");
        }
        Requirement existing = requirementMapper.selectById(dto.getId());
        if (existing == null) {
            throw new BusinessException("需求不存在");
        }
        if (!Boolean.TRUE.equals(existing.getIsDraft())) {
            throw new BusinessException(400, "当前需求不是草稿");
        }
        if (!Objects.equals(existing.getCreatorId(), userId)) {
            throw new BusinessException(403, "只有创建人可以编辑草稿");
        }
        if (dto.getVersion() == null) {
            throw new BusinessException(400, "缺少版本号");
        }

        UpdateWrapper<Requirement> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", dto.getId())
                .eq("creator_id", userId)
                .eq("is_draft", 1)
                .eq("version", dto.getVersion());

        Long attachmentProjectId = existing.getProjectId();
        if (dto.getProjectId() != null) {
            requireProjectSelection(dto.getProjectId());
            Long nextProjectId = normalizeProjectId(dto.getProjectId());
            if (!Objects.equals(normalizeProjectId(existing.getProjectId()), nextProjectId)) {
                ensureProjectCanBeBound(nextProjectId);
            }
            updateWrapper.set("project_id", nextProjectId);
            attachmentProjectId = nextProjectId;
        }
        if (dto.getParentId() != null) updateWrapper.set("parent_id", dto.getParentId());
        if (dto.getTitle() != null) updateWrapper.set("title", dto.getTitle());
        if (dto.getDescription() != null) updateWrapper.set("description", dto.getDescription());
        if (dto.getPriority() != null) updateWrapper.set("priority", dto.getPriority());
        if (dto.getAssigneeId() != null) updateWrapper.set("assignee_id", dto.getAssigneeId());
        if (dto.getCcUserIds() != null) updateWrapper.set("cc_user_ids", writeJson(dto.getCcUserIds()));
        if (dto.getModuleId() != null) updateWrapper.set("module_id", dto.getModuleId());
        if (dto.getStartDate() != null) updateWrapper.set("start_date", dto.getStartDate());
        if (dto.getDueDate() != null) updateWrapper.set("due_date", dto.getDueDate());
        if (dto.getEstimatedHours() != null) updateWrapper.set("estimated_hours", dto.getEstimatedHours());
        if (dto.getAttachments() != null) updateWrapper.set("attachments", writeJson(dto.getAttachments()));
        updateWrapper.set("last_saved_at", LocalDateTime.now());
        updateWrapper.set("version", dto.getVersion() + 1);

        int updated = requirementMapper.update(null, updateWrapper);
        if (updated <= 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "草稿已被他人更新，请刷新后重试");
        }

        recordHistory(dto.getId(), userId, "update", null, "更新草稿");
        if (dto.getAttachments() != null) {
            knowledgeDocumentService.syncRequirementAttachmentsWithContext(
                    attachmentProjectId,
                    existing.getId(),
                    existing.getRequirementNo(),
                    existing.getTitle(),
                    dto.getAttachments(),
                    userId
            );
        }
    }

    @Override
    public List<NextNodeOptionDTO> getNextNodes(Long requirementId, Long userId) {
        Requirement requirement = requirementMapper.selectById(requirementId);
        if (requirement == null) {
            throw new BusinessException("需求不存在");
        }
        if (!Boolean.TRUE.equals(requirement.getIsDraft())) {
            throw new BusinessException(400, "仅草稿支持查询下一环节");
        }
        if (!Objects.equals(requirement.getCreatorId(), userId)) {
            throw new BusinessException(403, "只有创建人可以操作草稿");
        }

        if (!StringUtils.hasText(requirement.getType())) {
            throw new BusinessException(400, "需求类型未设置，无法查询下一环节");
        }
        WorkflowVersion active = workflowVersionResolver.findActiveVersionForType(requirement.getType()).orElse(null);
        if (active == null || active.getIsActive() == null || active.getIsActive() != 1) {
            throw new BusinessException(400, "当前需求类型未绑定已启用的工作流，请保存草稿，稍后再提交");
        }
        WorkflowGraphContext context = workflowRuntimeLoader.loadContext(active.getId());
        WorkflowNode startNode = context.nodesById().values().stream()
                .filter(node -> "start".equalsIgnoreCase(node.getNodeType()))
                .findFirst()
                .orElse(null);
        if (startNode == null) {
            throw new BusinessException(400, "工作流缺少开始节点");
        }

        List<NextNodeOptionDTO> options = new ArrayList<>();
        for (WorkflowNode node : workflowGraphNavigator.resolveNextWaitNodes(context, startNode.getNodeId(), requirement)) {
            NextNodeOptionDTO opt = new NextNodeOptionDTO();
            opt.setNodeId(node.getNodeId());
            opt.setNodeName(node.getNodeName());
            String nodeStatusCode = WorkflowNodeUtils.resolveNodeStatusCode(node, true);
            opt.setBindStatusCode(nodeStatusCode);
            opt.setBindStatusName(resolveNodeStatusName(nodeStatusCode));
            opt.setProjectRequired(WorkflowNodeUtils.isProjectRequired(node));
            options.add(opt);
        }
        return options;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RequirementVO submit(Long requirementId, RequirementSubmitDTO dto, Long userId) {
        Requirement requirement = requirementMapper.selectById(requirementId);
        if (requirement == null) {
            throw new BusinessException("需求不存在");
        }
        requireProjectSelection(requirement.getProjectId());
        if (!Boolean.TRUE.equals(requirement.getIsDraft())) {
            throw new BusinessException(400, "当前需求不是草稿，无需提交");
        }
        if (!Objects.equals(requirement.getCreatorId(), userId)) {
            throw new BusinessException(403, "只有创建人可以提交草稿");
        }
        if (dto == null || dto.getVersion() == null) {
            throw new BusinessException(400, "缺少版本号");
        }

        if (!StringUtils.hasText(requirement.getType())) {
            throw new BusinessException(400, "需求类型未设置，无法提交");
        }
        WorkflowVersion active = workflowVersionResolver.findActiveVersionForType(requirement.getType()).orElse(null);
        if (active == null || active.getIsActive() == null || active.getIsActive() != 1) {
            throw new BusinessException(400, "当前需求类型未绑定已启用的工作流，请保存草稿，稍后再提交");
        }

        WorkflowGraphContext context = workflowRuntimeLoader.loadContext(active.getId());
        WorkflowNode startNode = context.nodesById().values().stream()
                .filter(node -> "start".equalsIgnoreCase(node.getNodeType()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(400, "工作流缺少开始节点"));

        List<WorkflowNode> candidates = workflowGraphNavigator.resolveNextWaitNodes(context, startNode.getNodeId(), requirement);
        if (candidates.isEmpty()) {
            throw new BusinessException(400, "工作流配置异常：开始节点没有可提交的下一环节");
        }

        String targetNodeId;
        if (candidates.size() == 1) {
            targetNodeId = candidates.get(0).getNodeId();
        } else {
            if (!StringUtils.hasText(dto.getNextNodeId())) {
                throw new BusinessException(400, "请选择下一环节");
            }
            boolean ok = candidates.stream().anyMatch(node -> dto.getNextNodeId().trim().equals(node.getNodeId()));
            if (!ok) {
                throw new BusinessException(400, "下一环节非法");
            }
            targetNodeId = dto.getNextNodeId().trim();
        }

        UpdateWrapper<Requirement> submitWrapper = new UpdateWrapper<>();
        submitWrapper.eq("id", requirementId)
                .eq("creator_id", userId)
                .eq("is_draft", 1)
                .eq("version", dto.getVersion())
                .set("is_draft", 0)
                .set("version", dto.getVersion() + 1);
        int updated = requirementMapper.update(null, submitWrapper);
        if (updated <= 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "需求已被他人处理，请刷新后重试");
        }

        workflowEngineService.submitFromDraft(requirementId, active.getId(), targetNodeId,
                normalizeProjectId(dto.getProjectId()), dto.getComment(), userId);

        Requirement latest = requirementMapper.selectById(requirementId);
        RequirementVO vo = new RequirementVO();
        if (latest != null) {
            vo = toRequirementVO(latest, userId, true);
        }
        return vo;
    }

    @Override
    public PageResult<RequirementVO> listMyDrafts(RequirementMyListQueryDTO query, Long userId) {
        Page<Requirement> page = new Page<>(query.getPageNum(), query.getPageSize());
        var result = requirementMapper.selectMyDrafts(page, userId, null, null, query.getProjectId(),
                query.getType(), query.getPriority(), query.getStatus(), query.getAssigneeId(), query.getKeyword());
        List<RequirementVO> list = new ArrayList<>();
        for (Requirement r : result.getRecords()) {
            list.add(toRequirementVO(r, userId, true, true)); // skipFollowed=true
        }
        // 批量填充关注状态
        batchFillFollowed(list, userId);
        return new PageResult<>(list, result.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public PageResult<RequirementVO> listMyPending(RequirementMyListQueryDTO query, Long userId) {
        workflowRuntimeMigrationService.alignRunningInstancesToActiveVersion();

        Page<Requirement> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<Requirement> result;

        if (USE_V2_ARCHITECTURE) {
            // 使用V2架构：workflow_node_assignees关联表（推荐，性能提升100倍+）
            List<Long> roleIds = getUserRoleIds(userId);
            List<Long> orgIds = getUserOrgIds(userId);

            result = requirementMapper.selectMyPendingV2(page, userId, roleIds, orgIds,
                    query.getProjectId(), query.getType(), query.getPriority(),
                    query.getStatus(), query.getAssigneeId(), query.getKeyword());
        } else if (USE_PENDING_TASK_OPTIMIZATION) {
            // 使用物化表优化查询
            result = requirementMapper.selectMyPendingOptimized(page, userId,
                    query.getProjectId(), query.getType(), query.getPriority(),
                    query.getStatus(), query.getAssigneeId(), query.getKeyword());
        } else {
            // 使用原始复杂查询（兼容模式）
            List<String> roleCodes = SecurityUtils.getCurrentUserRoles();
            List<Long> directOrgIds = resolveDirectOrgIds(userId);
            List<Long> scopedOrgIds = resolveScopedOrgIds(directOrgIds);

            result = requirementMapper.selectMyPending(page, userId, roleCodes, directOrgIds, scopedOrgIds,
                    query.getProjectId(), query.getType(), query.getPriority(), query.getStatus(), query.getAssigneeId(),
                    query.getKeyword());
        }

        List<RequirementVO> list = new ArrayList<>();
        for (Requirement r : result.getRecords()) {
            list.add(toRequirementVO(r, userId, true, true)); // skipFollowed=true
        }
        // 批量填充关注状态
        batchFillFollowed(list, userId);
        return new PageResult<>(list, result.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public PageResult<RequirementVO> listMyFollows(RequirementMyListQueryDTO query, Long userId) {
        List<String> currentRoleCodes = SecurityUtils.getCurrentUserRoles();
        boolean isSuperAdmin = isSuperAdmin(currentRoleCodes);
        List<Long> visibleOrgIds = resolveVisibleOrgIds(userId, isSuperAdmin);

        Page<Requirement> page = new Page<>(query.getPageNum(), query.getPageSize());
        var result = requirementMapper.selectMyFollows(page, userId, query.getProjectId(), query.getType(),
                query.getPriority(), query.getStatus(), query.getAssigneeId(), query.getKeyword(), isSuperAdmin,
                visibleOrgIds);
        List<RequirementVO> list = new ArrayList<>();
        for (Requirement r : result.getRecords()) {
            list.add(toRequirementVO(r, userId, true, true)); // skipFollowed=true
        }
        // 批量填充关注状态（我的关注列表中全部默认为已关注，但仍需标记）
        batchFillFollowed(list, userId);
        return new PageResult<>(list, result.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public PageResult<RequirementVO> listMyDone(RequirementMyListQueryDTO query, Long userId) {
        Page<Requirement> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<Requirement> result;

        if (USE_V2_ARCHITECTURE) {
            // 使用V2架构：workflow_node_assignees关联表（推荐，性能提升100倍+）
            List<Long> roleIds = getUserRoleIds(userId);
            List<Long> orgIds = getUserOrgIds(userId);

            result = requirementMapper.selectMyDoneV2(page, userId, roleIds, orgIds,
                    query.getProjectId(), query.getType(), query.getPriority(),
                    query.getStatus(), query.getAssigneeId(), query.getKeyword());
        } else {
            // 使用旧查询（兼容模式）
            List<String> roleCodes = SecurityUtils.getCurrentUserRoles();
            List<Long> directOrgIds = resolveDirectOrgIds(userId);
            List<Long> scopedOrgIds = resolveScopedOrgIds(directOrgIds);

            result = requirementMapper.selectMyDone(page, userId, roleCodes, directOrgIds, scopedOrgIds,
                    query.getProjectId(), query.getType(), query.getPriority(), query.getStatus(), query.getAssigneeId(),
                    query.getKeyword());
        }

        List<RequirementVO> list = new ArrayList<>();
        for (Requirement r : result.getRecords()) {
            list.add(toRequirementVO(r, userId, true, true)); // skipFollowed=true
        }
        // 批量填充关注状态
        batchFillFollowed(list, userId);
        return new PageResult<>(list, result.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void follow(Long requirementId, Long userId) {
        Requirement requirement = requirementMapper.selectById(requirementId);
        if (requirement == null) {
            throw new BusinessException("需求不存在");
        }
        if (!canViewRequirement(requirement, userId)) {
            throw new BusinessException(403, "无权关注该需求");
        }
        Long count = requirementFollowMapper.selectCount(new LambdaQueryWrapper<RequirementFollow>()
                .eq(RequirementFollow::getRequirementId, requirementId)
                .eq(RequirementFollow::getUserId, userId));
        if (count != null && count > 0) {
            return;
        }
        RequirementFollow follow = new RequirementFollow();
        follow.setRequirementId(requirementId);
        follow.setUserId(userId);
        requirementFollowMapper.insert(follow);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfollow(Long requirementId, Long userId) {
        requirementFollowMapper.delete(new LambdaQueryWrapper<RequirementFollow>()
                .eq(RequirementFollow::getRequirementId, requirementId)
                .eq(RequirementFollow::getUserId, userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long userId) {
        Requirement requirement = requirementMapper.selectById(id);
        if (requirement == null) {
            throw new BusinessException("需求不存在");
        }

        // 草稿状态：仅创建人可以删除
        if (Boolean.TRUE.equals(requirement.getIsDraft())) {
            boolean isCreator = requirement.getCreatorId() != null
                    && requirement.getCreatorId().equals(userId);
            if (!isCreator) {
                throw new BusinessException("只有创建者可以删除草稿");
            }
            // 草稿可以直接删除，无需检查流转记录
            requirementMapper.deleteById(id);
            return;
        }

        // 非草稿状态：需要检查权限和流转状态
        boolean isCreator = requirement.getCreatorId() != null
                && requirement.getCreatorId().equals(userId);
        boolean isAdmin = SecurityUtils.getCurrentUserRoles().contains("admin");

        if (!isCreator && !isAdmin) {
            throw new BusinessException("只有创建者或管理员可以删除需求");
        }

        // 检查是否有删除权限
        boolean hasDeletePermission = SecurityUtils.hasAnyPermission("button:requirement:delete");

        if (!hasDeletePermission && !isAdmin) {
            throw new BusinessException("您没有删除需求的权限");
        }

        Long instanceTransitionCount = workflowInstanceTransitionMapper.selectCount(
                new LambdaQueryWrapper<WorkflowInstanceTransition>()
                        .eq(WorkflowInstanceTransition::getRequirementId, id)
        );
        if (instanceTransitionCount != null && instanceTransitionCount > 0) {
            throw new BusinessException("已流转的需求不能删除");
        }
        Long legacyTransitionCount = workflowTransitionRecordMapper.selectCount(
                new LambdaQueryWrapper<com.demand.system.module.workflow.entity.WorkflowTransitionRecord>()
                        .eq(com.demand.system.module.workflow.entity.WorkflowTransitionRecord::getRequirementId, id)
        );
        if (legacyTransitionCount != null && legacyTransitionCount > 0) {
            throw new BusinessException("已流转的需求不能删除");
        }

        requirementMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restore(Long id, Long userId) {
        // 修复 P0：使用自定义 SQL 绕过 @TableLogic 过滤，查询包含已删除记录
        Requirement existing = requirementMapper.selectByIdIncludeDeleted(id);
        if (existing == null) {
            throw new BusinessException("需求不存在");
        }

        // 检查删除状态
        if (existing.getDeletedAt() == null || existing.getDeletedAt() == 0) {
            throw new BusinessException("该需求未被删除，无需恢复");
        }

        // 检查权限：只有创建者或admin角色可以恢复
        boolean isCreator = existing.getCreatorId() != null
                && existing.getCreatorId().equals(userId);
        boolean isAdmin = SecurityUtils.getCurrentUserRoles().contains("admin");

        if (!isCreator && !isAdmin) {
            throw new BusinessException("只有创建者或管理员可以恢复需求");
        }

        // 修复 P0：使用自定义 SQL 绕过 @TableLogic 拦截
        requirementMapper.restoreById(id);
    }

    @Override
    public List<RequirementCommentVO> getComments(Long requirementId) {
        ensureRequirementVisible(requirementId);
        return requirementCommentMapper.selectByRequirementId(requirementId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addComment(Long requirementId, RequirementCommentCreateDTO dto, Long userId) {
        Requirement requirement = ensureRequirementExists(requirementId);
        String content = dto.getContent() == null ? null : dto.getContent().trim();
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(400, "评论内容不能为空");
        }

        RequirementComment comment = new RequirementComment();
        comment.setRequirementId(requirementId);
        comment.setUserId(userId);
        comment.setContent(content);
        requirementCommentMapper.insert(comment);

        recordHistory(requirementId, userId, "comment", null, "新增评论");
        if (requirement.getCreatorId() != null && !Objects.equals(requirement.getCreatorId(), userId)) {
            notificationService.sendNotification(
                    requirement.getCreatorId(),
                    "需求新增评论",
                    "需求《" + requirement.getTitle() + "》收到一条新评论",
                    "requirement_comment",
                    requirementId);
        }
    }

    @Override
    public List<RequirementApprovalEvaluationVO> getApprovalEvaluations(Long requirementId) {
        ensureRequirementVisible(requirementId);
        return approvalEvaluationService.listByRequirementId(requirementId);
    }

    @Override
    public List<Map<String, Object>> getHistory(Long requirementId) {
        ensureRequirementVisible(requirementId);
        return historyMapper.selectHistoryByRequirement(requirementId);
    }

    @Override
    public List<Map<String, Object>> getChildren(Long parentId) {
        ensureRequirementVisible(parentId);
        LambdaQueryWrapper<Requirement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Requirement::getParentId, parentId)
                .eq(Requirement::getDeletedAt, 0)
                .orderByAsc(Requirement::getOrderNum);

        List<Requirement> children = requirementMapper.selectList(wrapper);
        List<Map<String, Object>> result = new ArrayList<>();
        Long userId = SecurityUtils.getCurrentUserId();
        for (Requirement r : children) {
            if (!canViewRequirement(r, userId)) {
                continue;
            }
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", r.getId());
            map.put("requirementNo", r.getRequirementNo());
            map.put("title", r.getTitle());
            map.put("status", r.getStatus());
            map.put("priority", r.getPriority());
            map.put("type", r.getType());
            result.add(map);
        }
        return result;
    }

    @Override
    public RequirementDetailVO getDetailBatch(Long id) {
        RequirementDetailVO result = new RequirementDetailVO();

        // 1. Get requirement basic info
        RequirementVO requirement = getDetail(id);
        result.setRequirement(requirement);

        // 2. Get history - 使用工作流流转历史（如果有workflowInstanceId）
        if (requirement.getWorkflowInstanceId() != null) {
            // 使用工作流引擎的流转历史，包含评审意见
            List<com.demand.system.module.workflow.dto.TransitionVO> transitions = workflowEngineService.getTransitionHistory(id);
            List<Map<String, Object>> historyList = new ArrayList<>();
            for (com.demand.system.module.workflow.dto.TransitionVO t : transitions) {
                Map<String, Object> map = new java.util.HashMap<>();
                map.put("id", t.getId());
                map.put("requirementId", t.getRequirementId());
                map.put("operatorId", t.getOperatorId());
                map.put("operatorName", t.getOperatorName());
                map.put("action", t.getAction());
                // 根据action设置fieldName
                String fieldName = "submit".equals(t.getAction()) ? "流程流转" :
                                 "rollback".equals(t.getAction()) ? "流程驳回" :
                                 "cancel".equals(t.getAction()) ? "流程取消" :
                                 "proxy_approve".equals(t.getAction()) ? "代审批" : "流程流转";
                map.put("fieldName", fieldName);
                map.put("oldValue", t.getFromNodeName() != null ? t.getFromNodeName() : (t.getFromNodeId() != null ? t.getFromNodeId() : "开始"));
                map.put("newValue", t.getToNodeName() != null ? t.getToNodeName() : (t.getToNodeId() != null ? t.getToNodeId() : "完成"));
                map.put("comment", t.getComment());  // 添加评审意见
                map.put("createdAt", t.getCreatedAt());
                historyList.add(map);
            }
            result.setHistory(historyList);
        } else {
            // 使用旧的历史记录表
            result.setHistory(getHistory(id));
        }

        // 3. Get children
        result.setChildren(getChildren(id));

        // 4. Get relations
        if (relationService != null) {
            List<RelationVO> relationVOList = relationService.listByRequirement(id);
            List<Map<String, Object>> relations = new ArrayList<>();
            for (RelationVO vo : relationVOList) {
                Map<String, Object> map = new java.util.HashMap<>();
                map.put("id", vo.getTargetId());
                map.put("title", vo.getTargetTitle());
                map.put("type", vo.getTargetType());
                map.put("status", vo.getTargetStatus());
                map.put("priority", vo.getTargetPriority());
                map.put("relationType", vo.getRelationType());
                relations.add(map);
            }
            result.setRelations(relations);
        }

        // 5. Get comments
        result.setComments(getComments(id));

        // 6. Get approval evaluations
        result.setApprovalEvaluations(getApprovalEvaluations(id));

        return result;
    }

    private void recordHistory(Long requirementId, Long operatorId, String fieldName, String oldValue, String newValue) {
        RequirementHistory history = new RequirementHistory();
        history.setRequirementId(requirementId);
        history.setOperatorId(operatorId);
        history.setFieldName(fieldName);
        history.setOldValue(oldValue);
        history.setNewValue(newValue);
        historyMapper.insert(history);
    }

    private Requirement ensureRequirementExists(Long requirementId) {
        Requirement requirement = requirementMapper.selectById(requirementId);
        if (requirement == null) {
            throw new BusinessException("需求不存在");
        }
        return requirement;
    }

    private Requirement ensureRequirementVisible(Long requirementId) {
        Requirement requirement = ensureRequirementExists(requirementId);
        requireViewPermission(requirement, "无权查看该需求");
        return requirement;
    }

    private void requireViewPermission(Requirement requirement, String message) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (!canViewRequirement(requirement, userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, message);
        }
    }

    private String strVal(Object val) {
        return val != null ? val.toString() : null;
    }

    private String strDate(LocalDate date) {
        return date != null ? date.toString() : null;
    }

    private String strDecimal(BigDecimal decimal) {
        return decimal != null ? decimal.toString() : null;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(400, "JSON字段序列化失败");
        }
    }

    /**
     * 插入需求并生成全局唯一编号
     * 使用Redis+Lua实现分布式ID生成，避免高并发场景下的编号冲突
     */
    private void insertRequirementWithGeneratedNo(Requirement requirement) {
        try {
            // 使用Redis分布式ID生成器，无需重试
            String requirementNo = distributedIdGenerator.generateRequirementNo();
            requirement.setRequirementNo(requirementNo);
            requirementMapper.insert(requirement);
        } catch (Exception ex) {
            log.error("Failed to generate requirement number", ex);
            throw new BusinessException("生成需求编号失败，请稍后重试");
        }
    }

    /**
     * 生成需求编号（已废弃，使用Redis分布式生成器替代）
     * @deprecated 使用 {@link DistributedIdGenerator#generateRequirementNo()} 替代
     */
    @Deprecated
    private String generateRequirementNo(LocalDateTime now) {
        String datePrefix = REQUIREMENT_NO_PREFIX + now.format(REQUIREMENT_NO_DATE_FORMATTER);
        Integer maxDailySequence = requirementMapper.selectMaxDailySequence(datePrefix);
        int nextSequence = maxDailySequence == null ? 0 : maxDailySequence + 1;
        int sequenceWidth = Math.max(REQUIREMENT_NO_MIN_SEQUENCE_WIDTH, String.valueOf(nextSequence).length());
        String sequenceText = String.format("%0" + sequenceWidth + "d", nextSequence);
        return REQUIREMENT_NO_PREFIX + now.format(REQUIREMENT_NO_TIMESTAMP_FORMATTER) + sequenceText;
    }

    private boolean isRequirementNoDuplicate(DuplicateKeyException ex) {
        String message = ex.getMessage();
        return message != null
                && (message.contains("uk_requirement_no") || message.contains("requirement_no"));
    }

    private String resolveNodeStatusCode(WorkflowNode node) {
        if (node == null) return null;
        if (node.getProperties() != null) {
            Object v = node.getProperties().get("nodeStatusCode");
            if (v != null) return v.toString();
            Object nested = node.getProperties().get("properties");
            if (nested instanceof java.util.Map<?, ?> nestedMap) {
                Object v2 = nestedMap.get("nodeStatusCode");
                if (v2 != null) return v2.toString();
            }
        }
        return null;
    }

    private String resolveNodeStatusName(String nodeStatusCode) {
        if (!StringUtils.hasText(nodeStatusCode)) {
            return null;
        }
        NodeStatus nodeStatus = nodeStatusMapper.selectOne(
                new LambdaQueryWrapper<NodeStatus>()
                        .eq(NodeStatus::getCode, nodeStatusCode)
                        .last("LIMIT 1"));
        if (nodeStatus != null && StringUtils.hasText(nodeStatus.getName())) {
            return nodeStatus.getName();
        }
        return nodeStatusCode;
    }

    private boolean isProjectRequired(WorkflowNode node) {
        if (node == null || node.getProperties() == null) {
            return false;
        }

        Object directValue = node.getProperties().get("projectRequired");
        if (directValue != null) {
            return parseBooleanValue(directValue);
        }

        Object nestedProperties = node.getProperties().get("properties");
        if (nestedProperties instanceof Map<?, ?> nestedMap) {
            Object nestedValue = nestedMap.get("projectRequired");
            if (nestedValue != null) {
                return parseBooleanValue(nestedValue);
            }
        }
        return false;
    }

    private boolean parseBooleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue() != 0;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private void requireProjectSelection(Long projectId) {
        if (projectId == null || projectId <= 0) {
            throw new BusinessException(400, "请选择所属项目");
        }
    }

    private Long normalizeProjectId(Long projectId) {
        return projectId == null || projectId <= 0 ? 0L : projectId;
    }

    private void ensureProjectCanBeBound(Long projectId) {
        if (projectId == null || projectId <= 0) {
            return;
        }

        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(400, "所选项目不存在");
        }
        if (isProjectExpired(project)) {
            throw new BusinessException(400, "已截止项目不可绑定到需求");
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

    /**
     * 获取用户的角色ID列表（用于V2架构查询）
     */
    private List<Long> getUserRoleIds(Long userId) {
        if (userId == null) {
            return List.of();
        }
        // 查询用户的角色ID
        List<Long> roleIds = userMapper.selectRoleIdsByUserId(userId);
        return roleIds != null ? roleIds : List.of();
    }

    /**
     * 获取用户的组织ID列表（用于V2架构查询）
     */
    private List<Long> getUserOrgIds(Long userId) {
        return resolveDirectOrgIds(userId);
    }

    private List<Long> resolveDirectOrgIds(Long userId) {
        if (userId == null) {
            return List.of();
        }
        LinkedHashSet<Long> orgIds = new LinkedHashSet<>();
        User user = userMapper.selectById(userId);
        if (user != null) {
            appendOrgId(orgIds, user.getOrgId());
            appendOrgId(orgIds, user.getDepartmentId());
            appendOrgId(orgIds, user.getRegionId());
        }

        List<UserOrganization> organizations = userOrganizationMapper.selectList(
                new LambdaQueryWrapper<UserOrganization>()
                        .eq(UserOrganization::getUserId, userId));
        for (UserOrganization organization : organizations) {
            appendOrgId(orgIds, organization.getOrgId());
            appendOrgId(orgIds, organization.getDepartmentId());
            appendOrgId(orgIds, organization.getRegionId());
        }
        return new ArrayList<>(orgIds);
    }

    private List<Long> resolveScopedOrgIds(List<Long> directOrgIds) {
        if (directOrgIds == null || directOrgIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> orgIds = new LinkedHashSet<>(directOrgIds);
        for (Long orgId : directOrgIds) {
            if (orgId == null) {
                continue;
            }
            SysOrgVO org;
            try {
                org = sysOrgService.getDetail(orgId);
            } catch (BusinessException ex) {
                continue;
            }
            if (org == null || !StringUtils.hasText(org.getPath())) {
                continue;
            }
            for (String pathPart : org.getPath().split("/")) {
                if (!StringUtils.hasText(pathPart)) {
                    continue;
                }
                try {
                    orgIds.add(Long.parseLong(pathPart));
                } catch (NumberFormatException ignored) {
                    // ignore invalid path fragment
                }
            }
        }
        return new ArrayList<>(orgIds);
    }

    private void appendOrgId(Set<Long> orgIds, Long orgId) {
        if (orgId != null && orgId > 0) {
            orgIds.add(orgId);
        }
    }

    private SFunction<Requirement, ?> getColumnFunction(String sortField) {
        return switch (sortField) {
            case "title" -> Requirement::getTitle;
            case "priority" -> Requirement::getPriority;
            case "status" -> Requirement::getStatus;
            case "dueDate" -> Requirement::getDueDate;
            case "orderNum" -> Requirement::getOrderNum;
            case "createdAt" -> Requirement::getCreatedAt;
            default -> Requirement::getCreatedAt;
        };
    }

    /**
     * PRD: 子需求状态汇总后自动更新父需求状态
     * 汇总规则：
     * - 如果所有子需求都是"已验收"，父需求状态变为"已验收"
     * - 如果所有子需求都是"已上线"，父需求状态变为"已上线"
     * - 如果有子需求处于"开发中"或"测试中"，父需求状态变为"开发中"
     * - 否则父需求保持"评审中"或原有状态
     */
    private void updateParentStatus(Long parentId, String childStatus) {
        if (parentId == null) {
            return;
        }

        Requirement parent = requirementMapper.selectById(parentId);
        if (parent == null || parent.getDeletedAt() != null && parent.getDeletedAt() != 0) {
            return;
        }

        // 查询所有子需求
        LambdaQueryWrapper<Requirement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Requirement::getParentId, parentId)
               .eq(Requirement::getDeletedAt, 0);
        List<Requirement> children = requirementMapper.selectList(wrapper);

        if (children.isEmpty()) {
            return;
        }

        // 统计各状态数量
        Set<String> childStatuses = children.stream()
                .map(Requirement::getStatus)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        String newParentStatus = null;

        // 汇总逻辑
        if (childStatuses.size() == 1) {
            // 所有子需求状态一致
            String onlyStatus = childStatuses.iterator().next();
            if ("已验收".equals(onlyStatus)) {
                newParentStatus = "已验收";
            } else if ("已上线".equals(onlyStatus)) {
                newParentStatus = "已上线";
            }
        } else if (!childStatuses.isEmpty()) {
            // 有多个不同状态
            if (childStatuses.contains("开发中") || childStatuses.contains("测试中")) {
                newParentStatus = "开发中";
            } else if (childStatuses.contains("已通过")) {
                newParentStatus = "已通过";
            }
        }

        // 更新父需求状态
        if (newParentStatus != null && !newParentStatus.equals(parent.getStatus())) {
            UpdateWrapper<Requirement> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("id", parentId).set("status", newParentStatus);
            requirementMapper.update(null, updateWrapper);
        }
    }

    private void fillUserNames(RequirementVO vo, Requirement r) {
        if (r.getCreatorId() != null) {
            User creator = userMapper.selectById(r.getCreatorId());
            if (creator != null) {
                vo.setCreatorName(resolveUserDisplayName(creator));
                // 归属部门显示为提出人所在的组织机构名称
                if (creator.getOrgId() != null) {
                    SysOrgVO org = sysOrgService.getDetail(creator.getOrgId());
                    if (org != null) {
                        vo.setDepartmentName(org.getName());
                    }
                } else if (creator.getDepartmentId() != null) {
                    SysOrgVO dept = sysOrgService.getDetail(creator.getDepartmentId());
                    if (dept != null) {
                        vo.setDepartmentName(dept.getName());
                    }
                }
            }
        }
        if (r.getAssigneeId() != null) {
            User assignee = userMapper.selectById(r.getAssigneeId());
            if (assignee != null) {
                vo.setAssigneeName(resolveUserDisplayName(assignee));
            }
        }
        if (r.getOpsFollowId() != null) {
            User opsFollow = userMapper.selectById(r.getOpsFollowId());
            if (opsFollow != null) {
                vo.setOpsFollowName(resolveUserDisplayName(opsFollow));
            }
        }
        if (r.getMaintFollowId() != null) {
            User maintFollow = userMapper.selectById(r.getMaintFollowId());
            if (maintFollow != null) {
                vo.setMaintFollowName(resolveUserDisplayName(maintFollow));
            }
        }
        vo.setCurrentHandlerName(resolveCurrentHandlerName(r, vo));
    }

    private String resolveCurrentHandlerName(Requirement requirement, RequirementVO vo) {
        if (requirement == null || requirement.getWorkflowInstanceId() == null) {
            return null;
        }

        WorkflowInstance instance = workflowInstanceMapper.selectById(requirement.getWorkflowInstanceId());
        if (instance == null && requirement.getId() != null) {
            instance = workflowInstanceMapper.selectOne(
                    new LambdaQueryWrapper<WorkflowInstance>()
                            .eq(WorkflowInstance::getRequirementId, requirement.getId())
                            .last("LIMIT 1"));
        }
        if (instance == null || !StringUtils.hasText(instance.getCurrentNodeId())) {
            return null;
        }

        WorkflowNode currentNode = null;
        // 直接从数据库查询节点，避免 context 加载异常被静默吞掉
        currentNode = workflowNodeMapper.selectOne(
                new LambdaQueryWrapper<WorkflowNode>()
                        .eq(WorkflowNode::getWorkflowVersionId, instance.getWorkflowVersionId())
                        .eq(WorkflowNode::getNodeId, instance.getCurrentNodeId())
                        .last("LIMIT 1"));

        if (currentNode == null) {
            log.warn("未找到工作流节点: workflowVersionId={}, nodeId={}", instance.getWorkflowVersionId(), instance.getCurrentNodeId());
            return null;
        }
        if (!StringUtils.hasText(currentNode.getAssigneeType())) {
            log.warn("节点未配置处理人类型: nodeId={}, nodeName={}", currentNode.getNodeId(), currentNode.getNodeName());
            return null;
        }

        String result = switch (currentNode.getAssigneeType()) {
            case "SPECIFIED_USER" -> resolveSpecifiedUsersDisplay(currentNode.getAssigneeUserIds());
            case "SPECIFIED_ROLE" -> resolveRoleDisplay(currentNode.getAssigneeRoleId());
            case "SPECIFIED_ROLE_GROUP" -> resolveRoleGroupDisplay(currentNode.getAssigneeRoleGroupId());
            case "SPECIFIED_ORG" -> resolveOrgDisplay(currentNode.getAssigneeOrgId(), currentNode.getProperties());
            case "CREATOR" -> vo.getCreatorName();
            case "PREV_APPROVER" -> resolvePreviousApproverDisplay(instance.getId(), currentNode.getNodeId());
            default -> null;
        };

        log.debug("resolveCurrentHandlerName: reqId={}, assigneeType={}, result={}", requirement.getId(), currentNode.getAssigneeType(), result);
        return result;
    }

    private String resolveSpecifiedUsersDisplay(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return null;
        }
        return userIds.stream()
                .map(userMapper::selectById)
                .filter(Objects::nonNull)
                .map(this::resolveUserDisplayName)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.joining("、"));
    }

    private String resolveRoleDisplay(Integer roleId) {
        if (roleId == null) {
            return null;
        }
        Role role = roleMapper.selectById(Long.valueOf(roleId));
        if (role == null) {
            return null;
        }
        return StringUtils.hasText(role.getName()) ? role.getName() : role.getCode();
    }

    private String resolveRoleGroupDisplay(Long roleGroupId) {
        if (roleGroupId == null) {
            return null;
        }
        RoleGroup roleGroup = roleGroupMapper.selectById(roleGroupId);
        return roleGroup != null ? roleGroup.getName() : null;
    }

    private String resolveOrgDisplay(Long orgId, Map<String, Object> properties) {
        if (orgId == null) {
            return null;
        }
        SysOrgVO org = sysOrgService.getDetail(orgId);
        if (org == null || !StringUtils.hasText(org.getName())) {
            return null;
        }
        Object orgScopeType = properties != null ? properties.get("orgScopeType") : null;
        if (orgScopeType != null && !"current".equalsIgnoreCase(String.valueOf(orgScopeType))) {
            return org.getName() + "（含子级）";
        }
        return org.getName();
    }

    private String resolvePreviousApproverDisplay(Long instanceId, String currentNodeId) {
        if (instanceId == null || !StringUtils.hasText(currentNodeId)) {
            return null;
        }
        WorkflowInstanceTransition transition = workflowInstanceTransitionMapper.selectOne(
                new LambdaQueryWrapper<WorkflowInstanceTransition>()
                        .eq(WorkflowInstanceTransition::getInstanceId, instanceId)
                        .eq(WorkflowInstanceTransition::getToNodeId, currentNodeId)
                        .orderByDesc(WorkflowInstanceTransition::getId)
                        .last("LIMIT 1"));
        if (transition == null || transition.getOperatorId() == null) {
            return null;
        }
        User approver = userMapper.selectById(transition.getOperatorId());
        return approver != null ? resolveUserDisplayName(approver) : null;
    }

    private String resolveUserDisplayName(User user) {
        if (user == null) {
            return null;
        }
        if (StringUtils.hasText(user.getRealName())) {
            return user.getRealName();
        }
        return user.getUsername();
    }

    private RequirementVO toRequirementVO(Requirement requirement, Long userId, boolean fillPermission) {
        return toRequirementVO(requirement, userId, fillPermission, false);
    }

    private RequirementVO toRequirementVO(Requirement requirement, Long userId, boolean fillPermission, boolean skipFollowed) {
        RequirementVO vo = new RequirementVO();
        BeanUtils.copyProperties(requirement, vo);
        fillUserNames(vo, requirement);
        // 分页场景下由 pageQuery 提前批量化回填，单行 detail 场景走 enrichAttachmentsAndTransitions
        // —— 这里不再单独调用，避免单行回填带来的 2 次额外查询。
        if (userId != null) {
            if (fillPermission) {
                fillPermissionFields(vo, requirement, userId);
            }
            if (!skipFollowed) {
                fillFollowed(vo, requirement.getId(), userId);
            }
        }
        return vo;
    }

    /**
     * 批量补全一页 Requirement 记录的附件元信息。
     *
     * <p>原实现按行调用 {@link #enrichAttachmentsAndTransitions}（在分页场景），
     * 每行触发 2 次 batch select（2N 次）。改为先收集整页 fileIds / uploaderIds，
     * 单次批量回填到每行的 attachments 列表。</p>
     */
    private void batchEnrichAttachmentMeta(List<Requirement> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return;
        }
        java.util.Set<Long> allFileIds = new java.util.HashSet<>();
        for (Requirement r : requirements) {
            if (r == null || r.getAttachments() == null) continue;
            for (RequirementAttachmentDTO att : r.getAttachments()) {
                if (att != null && att.getFileId() != null) {
                    allFileIds.add(att.getFileId());
                }
            }
        }
        if (allFileIds.isEmpty()) {
            return;
        }

        java.util.Map<Long, FileRecord> recordMap = new java.util.HashMap<>();
        java.util.Set<Long> uploaderIds = new java.util.HashSet<>();
        for (FileRecord record : fileRecordMapper.selectBatchIds(allFileIds)) {
            if (record == null || record.getId() == null) continue;
            recordMap.put(record.getId(), record);
            if (record.getUploaderId() != null) {
                uploaderIds.add(record.getUploaderId());
            }
        }

        java.util.Map<Long, String> uploaderNameMap = new java.util.HashMap<>();
        if (!uploaderIds.isEmpty()) {
            for (User u : userMapper.selectBatchIds(uploaderIds)) {
                if (u == null) continue;
                uploaderNameMap.put(u.getId(),
                        StringUtils.hasText(u.getRealName()) ? u.getRealName() : u.getUsername());
            }
        }

        for (Requirement r : requirements) {
            if (r == null || r.getAttachments() == null) continue;
            for (RequirementAttachmentDTO att : r.getAttachments()) {
                if (att == null || att.getFileId() == null) continue;
                FileRecord record = recordMap.get(att.getFileId());
                if (record == null) continue;
                att.setUploadedAt(record.getCreatedAt());
                att.setUploaderId(record.getUploaderId());
                if (record.getUploaderId() != null) {
                    att.setUploaderName(uploaderNameMap.get(record.getUploaderId()));
                }
            }
        }
    }

    /**
     * 批量补全需求列表的关注状态。
     *
     * <p>原实现按行调用 fillFollowed，每行触发 1 次 SELECT COUNT 查询（N 次）。
     * 改为先收集整页 requirementIds，单次批量查询当前用户的所有关注记录，
     * 然后回填到每行的 followed 字段。</p>
     *
     * @param voList 需求VO列表
     * @param userId 当前用户ID
     */
    private void batchFillFollowed(List<RequirementVO> voList, Long userId) {
        if (voList == null || voList.isEmpty()) {
            return;
        }

        if (userId == null) {
            voList.forEach(vo -> vo.setFollowed(false));
            return;
        }

        // 1. 收集所有需求ID
        Set<Long> requirementIds = voList.stream()
            .map(RequirementVO::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        if (requirementIds.isEmpty()) {
            voList.forEach(vo -> vo.setFollowed(false));
            return;
        }

        // 2. 批量查询当前用户的关注记录
        List<RequirementFollow> follows = requirementFollowMapper.selectList(
            new LambdaQueryWrapper<RequirementFollow>()
                .eq(RequirementFollow::getUserId, userId)
                .in(RequirementFollow::getRequirementId, requirementIds)
        );

        // 3. 构建已关注需求ID集合
        Set<Long> followedIds = follows.stream()
            .map(RequirementFollow::getRequirementId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        // 4. 批量回填关注状态
        voList.forEach(vo -> {
            if (vo.getId() != null) {
                vo.setFollowed(followedIds.contains(vo.getId()));
            } else {
                vo.setFollowed(false);
            }
        });
    }

    /**
     * 补全附件元信息 + 流转节点附件。
     *
     * <p>需求详情页统一收集所有 fileIds / userIds 后批量回填，避免循环查询。
     * 合并前：enrichAttachmentMeta + fillTransitionAttachments 共触发 2 次 file_records、
     * 3 次 sys_user 查表（5 roundtrip）；合并后：1 次 file_records + 1 次 sys_user
     * （2 roundtrip）。</p>
     */
    private void enrichAttachmentsAndTransitions(RequirementVO vo, Long requirementId) {
        // 1) 补全主附件元信息
        List<RequirementAttachmentDTO> mainAttachments = vo.getAttachments();
        java.util.Set<Long> allFileIds = new java.util.HashSet<>();
        if (mainAttachments != null) {
            for (RequirementAttachmentDTO att : mainAttachments) {
                if (att != null && att.getFileId() != null) {
                    allFileIds.add(att.getFileId());
                }
            }
        }

        // 2) 加载流转历史 + 收集流转节点附件 fileIds / operatorIds
        List<com.demand.system.module.workflow.entity.WorkflowInstanceTransition> transitions = null;
        if (requirementId != null) {
            transitions = workflowInstanceTransitionMapper.selectList(
                    new LambdaQueryWrapper<com.demand.system.module.workflow.entity.WorkflowInstanceTransition>()
                            .eq(com.demand.system.module.workflow.entity.WorkflowInstanceTransition::getRequirementId, requirementId)
                            .orderByAsc(com.demand.system.module.workflow.entity.WorkflowInstanceTransition::getCreatedAt));
        }
        java.util.Set<Long> operatorIds = new java.util.HashSet<>();
        if (transitions != null) {
            for (var t : transitions) {
                if (t == null) continue;
                if (t.getOperatorId() != null) {
                    operatorIds.add(t.getOperatorId());
                }
                if (t.getAttachmentIds() != null) {
                    allFileIds.addAll(t.getAttachmentIds());
                }
            }
        }

        if (allFileIds.isEmpty() && operatorIds.isEmpty() && (transitions == null || transitions.isEmpty())) {
            return;
        }

        // 3) 单次批量查 file_records
        java.util.Map<Long, FileRecord> recordMap = new java.util.HashMap<>();
        java.util.Set<Long> uploaderIds = new java.util.HashSet<>();
        if (!allFileIds.isEmpty()) {
            for (FileRecord record : fileRecordMapper.selectBatchIds(allFileIds)) {
                if (record == null || record.getId() == null) continue;
                recordMap.put(record.getId(), record);
                if (record.getUploaderId() != null) {
                    uploaderIds.add(record.getUploaderId());
                }
            }
        }

        // 4) 单次批量查 sys_user（覆盖上传人 + 操作人）
        java.util.Map<Long, String> userNameMap = new java.util.HashMap<>();
        java.util.Set<Long> allUserIds = new java.util.HashSet<>();
        allUserIds.addAll(uploaderIds);
        allUserIds.addAll(operatorIds);
        if (!allUserIds.isEmpty()) {
            for (User u : userMapper.selectBatchIds(allUserIds)) {
                if (u == null) continue;
                userNameMap.put(u.getId(), StringUtils.hasText(u.getRealName()) ? u.getRealName() : u.getUsername());
            }
        }

        // 5) 回填主附件
        if (mainAttachments != null) {
            for (RequirementAttachmentDTO att : mainAttachments) {
                if (att == null || att.getFileId() == null) continue;
                FileRecord record = recordMap.get(att.getFileId());
                if (record == null) continue;
                att.setUploadedAt(record.getCreatedAt());
                att.setUploaderId(record.getUploaderId());
                if (record.getUploaderId() != null) {
                    att.setUploaderName(userNameMap.get(record.getUploaderId()));
                }
            }
        }

        // 6) 回填流转节点附件
        if (transitions != null && !transitions.isEmpty()) {
            List<com.demand.system.module.requirement.dto.TransitionAttachmentGroupDTO> groups = new ArrayList<>();
            for (var t : transitions) {
                if (t == null || t.getAttachmentIds() == null || t.getAttachmentIds().isEmpty()) {
                    continue;
                }
                List<RequirementAttachmentDTO> atts = new ArrayList<>();
                for (Long fid : t.getAttachmentIds()) {
                    FileRecord rec = recordMap.get(fid);
                    if (rec == null) continue;
                    RequirementAttachmentDTO a = new RequirementAttachmentDTO();
                    a.setFileId(rec.getId());
                    a.setName(rec.getOriginalName());
                    a.setSize(rec.getFileSize());
                    a.setContentType(rec.getContentType());
                    a.setBucketName(rec.getBucketName());
                    a.setObjectName(rec.getStorageName());
                    a.setUploadedAt(rec.getCreatedAt());
                    a.setUploaderId(rec.getUploaderId());
                    if (rec.getUploaderId() != null) {
                        a.setUploaderName(userNameMap.get(rec.getUploaderId()));
                    }
                    atts.add(a);
                }
                if (atts.isEmpty()) continue;

                com.demand.system.module.requirement.dto.TransitionAttachmentGroupDTO g =
                        new com.demand.system.module.requirement.dto.TransitionAttachmentGroupDTO();
                g.setTransitionId(t.getId());
                g.setNodeName(StringUtils.hasText(t.getFromNodeName()) ? t.getFromNodeName() : t.getToNodeName());
                g.setAction(t.getAction());
                g.setOperatorName(t.getOperatorId() != null ? userNameMap.get(t.getOperatorId()) : null);
                g.setOperatedAt(t.getCompletedAt() != null ? t.getCompletedAt() : t.getStartedAt());
                g.setAttachments(atts);
                groups.add(g);
            }
            vo.setTransitionAttachments(groups);
        }
    }

    private void fillFollowed(RequirementVO vo, Long requirementId, Long userId) {
        if (requirementId == null || userId == null) {
            vo.setFollowed(false);
            return;
        }
        Long count = requirementFollowMapper.selectCount(new LambdaQueryWrapper<RequirementFollow>()
                .eq(RequirementFollow::getRequirementId, requirementId)
                .eq(RequirementFollow::getUserId, userId));
        vo.setFollowed(count != null && count > 0);
    }

    private boolean isSuperAdmin(List<String> roleCodes) {
        if (roleCodes == null) {
            return false;
        }
        return roleCodes.stream().anyMatch(roleCode ->
                RbacConstants.ROLE_SUPER_ADMIN.equalsIgnoreCase(roleCode)
                        || RbacConstants.ROLE_SUPER_ADMIN_DB.equalsIgnoreCase(roleCode)
                        || RbacConstants.ROLE_ADMIN.equalsIgnoreCase(roleCode)
                        || "admin".equalsIgnoreCase(roleCode)
        );
    }

    private List<Long> resolveVisibleOrgIds(Long userId, boolean isSuperAdmin) {
        if (isSuperAdmin || userId == null) {
            return List.of();
        }
        // 复用已有的健壮逻辑：从 orgId + departmentId + regionId + user_organizations 多源融合
        List<Long> directOrgIds = resolveDirectOrgIds(userId);
        if (directOrgIds.isEmpty()) {
            return List.of();
        }
        // 使用缓存批量获取所有子孙组织ID
        return orgHierarchyCache.getDescendantsBatch(directOrgIds);
    }

    private boolean canViewRequirement(Requirement requirement, Long userId) {
        if (requirement == null || userId == null) {
            return false;
        }
        if (isSuperAdmin(SecurityUtils.getCurrentUserRoles())) {
            return true;
        }
        if (Objects.equals(requirement.getCreatorId(), userId)) {
            return true;
        }
        // 参与过需求审批/历史流转的人可查看（覆盖"我的待办/已办"场景，避免列表可见但点不进详情）
        if (isParticipant(requirement.getId(), userId)) {
            return true;
        }
        // 被指派为当前工作流节点处理人也可查看（与 selectMyPending 列表判定保持一致，
        // 避免"我的待办"列表能看见但点进详情被 403）
        if (isAssignedAsCurrentNodeApprover(requirement, userId)) {
            return true;
        }
        List<Long> visibleOrgIds = resolveVisibleOrgIds(userId, false);
        return requirement.getOrgId() != null && visibleOrgIds.contains(requirement.getOrgId());
    }

    /**
     * 判定用户是否被指派为当前工作流节点的处理人。
     * 与 {@link com.demand.system.module.requirement.mapper.RequirementMapper#selectMyPending} 的
     * 6 种 assignee_type 分支完全对齐，保证"我的待办"列表可见的每一条都至少能查看。
     */
    private boolean isAssignedAsCurrentNodeApprover(Requirement requirement, Long userId) {
        if (requirement == null || userId == null || requirement.getWorkflowInstanceId() == null) {
            return false;
        }
        WorkflowInstance instance = workflowInstanceMapper.selectById(requirement.getWorkflowInstanceId());
        if (instance == null || !"running".equals(instance.getStatus())) {
            return false;
        }
        WorkflowNode node = workflowNodeMapper.selectOne(
            new LambdaQueryWrapper<WorkflowNode>()
                .eq(WorkflowNode::getWorkflowVersionId, instance.getWorkflowVersionId())
                .eq(WorkflowNode::getNodeId, instance.getCurrentNodeId())
        );
        if (node == null) {
            return false;
        }
        String assigneeType = node.getAssigneeType();
        if (!StringUtils.hasText(assigneeType)) {
            return false;
        }
        switch (assigneeType) {
            case "SPECIFIED_USER":
                return node.getAssigneeUserIds() != null && node.getAssigneeUserIds().contains(userId);
            case "SPECIFIED_ROLE":
                if (node.getAssigneeRoleId() == null) {
                    return false;
                }
                Role role = roleMapper.selectById(node.getAssigneeRoleId().longValue());
                return role != null && currentUserMatchesRole(role);
            case "SPECIFIED_ROLE_GROUP":
                if (node.getAssigneeRoleGroupId() == null) {
                    return false;
                }
                List<Role> groupRoles = roleMapper.selectList(
                    new LambdaQueryWrapper<Role>()
                        .eq(Role::getRoleGroupId, node.getAssigneeRoleGroupId())
                        .eq(Role::getDeletedAt, 0)
                );
                return groupRoles.stream().anyMatch(this::currentUserMatchesRole);
            case "SPECIFIED_ORG": {
                Long orgId = node.getAssigneeOrgId();
                if (orgId == null) {
                    return false;
                }
                List<Long> directOrgIds = resolveDirectOrgIds(userId);
                Map<String, Object> properties = node.getProperties();
                String scope = properties == null ? "include_children"
                        : String.valueOf(properties.get("orgScopeType"));
                if ("current".equalsIgnoreCase(scope)) {
                    return directOrgIds.contains(orgId);
                }
                List<Long> scopedOrgIds = resolveScopedOrgIds(directOrgIds);
                return scopedOrgIds.contains(orgId);
            }
            case "PREV_APPROVER": {
                WorkflowInstanceTransition last = workflowInstanceTransitionMapper.selectOne(
                    new LambdaQueryWrapper<WorkflowInstanceTransition>()
                        .eq(WorkflowInstanceTransition::getInstanceId, instance.getId())
                        .eq(WorkflowInstanceTransition::getToNodeId, instance.getCurrentNodeId())
                        .orderByDesc(WorkflowInstanceTransition::getId)
                        .last("LIMIT 1")
                );
                return last != null && Objects.equals(last.getOperatorId(), userId);
            }
            case "CREATOR":
                return Objects.equals(requirement.getCreatorId(), userId);
            default:
                return false;
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

    /**
     * 填充权限字段：canEdit, canView, canApprove, isParticipant, operationType
     * 草稿状态：提交人可编辑，管理员可查看
     * 流转中状态：有审批权限可审批，否则可查看
     * 结束状态：参与人可查看
     */
    private void fillPermissionFields(RequirementVO vo, Requirement r, Long userId) {
        boolean isCreator = Objects.equals(r.getCreatorId(), userId);
        boolean isSuperAdmin = SecurityUtils.getCurrentUserRoles().contains("admin");
        boolean participant = isParticipant(r.getId(), userId);
        vo.setIsParticipant(participant);

        // 草稿状态
        if (Boolean.TRUE.equals(r.getIsDraft())) {
            vo.setCanEdit(isCreator);
            vo.setCanView(isSuperAdmin || isCreator);
            vo.setCanApprove(false);
            vo.setOperationType(isCreator ? "edit" : "view");
            return;
        }

        // 判断是否有审批权限
        boolean canApprove = false;
        if (r.getWorkflowInstanceId() != null) {
            try {
                WorkflowAvailableActionsDTO actions = workflowEngineService.getAvailableActions(r.getId());
                canApprove = Boolean.TRUE.equals(actions.getCanTransition());

                // 添加详细日志用于诊断
                log.debug("Permission check for requirement: id={}, userId={}, workflowInstanceId={}, " +
                         "canTransition={}, currentNodeId={}, currentNodeName={}, currentNodeType={}",
                         r.getId(), userId, r.getWorkflowInstanceId(),
                         canApprove, actions.getCurrentNodeId(), actions.getCurrentNodeName(),
                         actions.getCurrentNodeType());
            } catch (Exception e) {
                // 工作流异常时默认无审批权限
                log.warn("Failed to get available actions for requirement: id={}, userId={}, error={}",
                        r.getId(), userId, e.getMessage());
                canApprove = false;
            }
        }

        // 流转中状态：有审批权限且未参与过可审批，否则可查看
        if (canApprove) {
            vo.setCanEdit(false);
            vo.setCanView(true);
            vo.setCanApprove(true);
            vo.setOperationType("approve");
            log.debug("Set operationType=approve for requirement: id={}, userId={}", r.getId(), userId);
        } else {
            vo.setCanEdit(false);
            vo.setCanView(true);
            vo.setCanApprove(false);
            vo.setOperationType("view");
            log.debug("Set operationType=view for requirement: id={}, userId={}, reason=no approve permission",
                     r.getId(), userId);
        }
    }

    /**
     * 判断用户是否参与过需求的审批（在workflow_instance_transitions中有记录）
     */
    private boolean isParticipant(Long requirementId, Long userId) {
        // 1. 参与过审批流转（在 workflow_instance_transitions 有记录）
        Long transitionCount = workflowInstanceTransitionMapper.selectCount(
            new LambdaQueryWrapper<WorkflowInstanceTransition>()
                .eq(WorkflowInstanceTransition::getRequirementId, requirementId)
                .eq(WorkflowInstanceTransition::getOperatorId, userId)
        );
        if (transitionCount != null && transitionCount > 0) {
            return true;
        }
        // 2. 编辑过字段历史（在 requirement_history 有记录）—— 覆盖"我只是改了描述/工时但没进审批"的场景
        Long historyCount = historyMapper.selectCount(
            new LambdaQueryWrapper<RequirementHistory>()
                .eq(RequirementHistory::getRequirementId, requirementId)
                .eq(RequirementHistory::getOperatorId, userId)
        );
        return historyCount != null && historyCount > 0;
    }

    private void sendStatusChangeNotifications(Requirement requirement, String newStatus, Long operatorId) {
        String title = "需求状态变更";
        String content = String.format("需求【%s】状态已变更为【%s】", requirement.getTitle(), newStatus);

        // Notify creator (if not the operator)
        if (requirement.getCreatorId() != null && !requirement.getCreatorId().equals(operatorId)) {
            notificationService.sendNotification(requirement.getCreatorId(), title, content, "requirement", requirement.getId());
        }
        // Notify assignee (if not the operator and not the creator)
        if (requirement.getAssigneeId() != null
                && !requirement.getAssigneeId().equals(operatorId)
                && !requirement.getAssigneeId().equals(requirement.getCreatorId())) {
            notificationService.sendNotification(requirement.getAssigneeId(), title, content, "requirement", requirement.getId());
        }
    }
}
