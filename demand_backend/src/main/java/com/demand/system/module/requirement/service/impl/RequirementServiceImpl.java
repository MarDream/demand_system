package com.demand.system.module.requirement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.PageResult;
import com.demand.system.common.result.Result;
import com.demand.system.module.requirement.dto.RequirementCreateDTO;
import com.demand.system.module.requirement.dto.RequirementCommentCreateDTO;
import com.demand.system.module.requirement.dto.RequirementCommentVO;
import com.demand.system.module.requirement.dto.RequirementDraftCreateDTO;
import com.demand.system.module.requirement.dto.RequirementDraftUpdateDTO;
import com.demand.system.module.requirement.dto.RequirementMyListQueryDTO;
import com.demand.system.module.requirement.dto.RequirementSubmitDTO;
import com.demand.system.module.requirement.dto.NextNodeOptionDTO;
import com.demand.system.module.requirement.dto.RequirementQueryDTO;
import com.demand.system.module.requirement.dto.RequirementUpdateDTO;
import com.demand.system.module.requirement.dto.RequirementVO;
import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.requirement.entity.RequirementComment;
import com.demand.system.module.requirement.entity.RequirementHistory;
import com.demand.system.module.requirement.entity.RequirementTypeConfig;
import com.demand.system.module.requirement.mapper.CustomFieldValueMapper;
import com.demand.system.module.requirement.mapper.RequirementCommentMapper;
import com.demand.system.module.requirement.mapper.RequirementHistoryMapper;
import com.demand.system.module.requirement.mapper.RequirementMapper;
import com.demand.system.module.requirement.service.RequirementConfigService;
import com.demand.system.module.requirement.service.RequirementService;
import com.demand.system.module.user.entity.User;
import com.demand.system.module.user.mapper.UserMapper;
import com.demand.system.module.organization.dto.SysOrgVO;
import com.demand.system.module.organization.service.SysOrgService;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.notification.service.NotificationService;
import com.demand.system.module.project.entity.Project;
import com.demand.system.module.project.mapper.ProjectMapper;
import com.demand.system.module.knowledge.service.KnowledgeDocumentService;
import com.demand.system.module.workflow.entity.WorkflowEdge;
import com.demand.system.module.workflow.entity.WorkflowInstance;
import com.demand.system.module.workflow.entity.WorkflowNode;
import com.demand.system.module.workflow.entity.WorkflowVersion;
import com.demand.system.module.workflow.engine.WorkflowVersionResolver;
import com.demand.system.module.workflow.entity.NodeStatus;
import com.demand.system.module.workflow.mapper.WorkflowEdgeMapper;
import com.demand.system.module.workflow.mapper.WorkflowInstanceMapper;
import com.demand.system.module.workflow.mapper.NodeStatusMapper;
import com.demand.system.module.workflow.mapper.WorkflowNodeMapper;
import com.demand.system.module.workflow.mapper.WorkflowVersionMapper;
import com.demand.system.module.workflow.service.WorkflowEngineService;
import com.demand.system.module.workflow.service.WorkflowService;
import com.demand.system.module.workflow.mapper.WorkflowTransitionRecordMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RequirementServiceImpl implements RequirementService {

    private static final String REQUIREMENT_NO_PREFIX = "BR";
    private static final int REQUIREMENT_NO_MIN_SEQUENCE_WIDTH = 3;
    private static final int REQUIREMENT_NO_MAX_RETRY = 5;
    private static final DateTimeFormatter REQUIREMENT_NO_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter REQUIREMENT_NO_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final RequirementMapper requirementMapper;
    private final RequirementHistoryMapper historyMapper;
    private final RequirementCommentMapper requirementCommentMapper;
    private final CustomFieldValueMapper customFieldValueMapper;
    private final UserMapper userMapper;
    private final SysOrgService sysOrgService;
    private final NotificationService notificationService;
    private final WorkflowService workflowService;
    private final WorkflowEngineService workflowEngineService;
    private final WorkflowVersionMapper workflowVersionMapper;
    private final WorkflowVersionResolver workflowVersionResolver;
    private final WorkflowNodeMapper workflowNodeMapper;
    private final WorkflowEdgeMapper workflowEdgeMapper;
    private final WorkflowInstanceMapper workflowInstanceMapper;
    private final WorkflowTransitionRecordMapper workflowTransitionRecordMapper;
    private final RequirementConfigService requirementConfigService;
    private final KnowledgeDocumentService knowledgeDocumentService;
    private final NodeStatusMapper nodeStatusMapper;
    private final ProjectMapper projectMapper;

    public RequirementServiceImpl(RequirementMapper requirementMapper, RequirementHistoryMapper historyMapper, RequirementCommentMapper requirementCommentMapper, CustomFieldValueMapper customFieldValueMapper, UserMapper userMapper, SysOrgService sysOrgService, NotificationService notificationService, WorkflowService workflowService, WorkflowEngineService workflowEngineService, WorkflowVersionMapper workflowVersionMapper, WorkflowVersionResolver workflowVersionResolver, WorkflowNodeMapper workflowNodeMapper, WorkflowEdgeMapper workflowEdgeMapper, WorkflowInstanceMapper workflowInstanceMapper, WorkflowTransitionRecordMapper workflowTransitionRecordMapper, RequirementConfigService requirementConfigService, KnowledgeDocumentService knowledgeDocumentService, NodeStatusMapper nodeStatusMapper, ProjectMapper projectMapper) {
        this.requirementMapper = requirementMapper;
        this.historyMapper = historyMapper;
        this.requirementCommentMapper = requirementCommentMapper;
        this.customFieldValueMapper = customFieldValueMapper;
        this.userMapper = userMapper;
        this.sysOrgService = sysOrgService;
        this.notificationService = notificationService;
        this.workflowService = workflowService;
        this.workflowEngineService = workflowEngineService;
        this.workflowVersionMapper = workflowVersionMapper;
        this.workflowVersionResolver = workflowVersionResolver;
        this.workflowNodeMapper = workflowNodeMapper;
        this.workflowEdgeMapper = workflowEdgeMapper;
        this.workflowInstanceMapper = workflowInstanceMapper;
        this.workflowTransitionRecordMapper = workflowTransitionRecordMapper;
        this.requirementConfigService = requirementConfigService;
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.nodeStatusMapper = nodeStatusMapper;
        this.projectMapper = projectMapper;
    }

    @Override
    public PageResult<RequirementVO> list(RequirementQueryDTO query) {
        Page<Requirement> page = new Page<>(query.getPageNum(), query.getPageSize());

        LambdaQueryWrapper<Requirement> wrapper = new LambdaQueryWrapper<>();

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

        List<RequirementVO> voList = new ArrayList<>();
        for (Requirement r : resultPage.getRecords()) {
            RequirementVO vo = new RequirementVO();
            BeanUtils.copyProperties(r, vo);
            fillUserNames(vo, r);
            voList.add(vo);
        }

        return new PageResult<>(voList, resultPage.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public RequirementVO getDetail(Long id) {
        Requirement r = requirementMapper.selectById(id);
        if (r == null) {
            throw new BusinessException("需求不存在");
        }
        RequirementVO vo = new RequirementVO();
        BeanUtils.copyProperties(r, vo);
        fillUserNames(vo, r);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(RequirementCreateDTO dto, Long creatorId) {
        requireProjectSelection(dto.getProjectId());
        Requirement requirement = new Requirement();
        BeanUtils.copyProperties(dto, requirement);
        requirement.setProjectId(normalizeProjectId(dto.getProjectId()));
        ensureProjectCanBeBound(requirement.getProjectId());
        RequirementTypeConfig defaultType = requirementConfigService.getDefaultType();
        if (defaultType == null || !StringUtils.hasText(defaultType.getCode())) {
            throw new BusinessException("请先配置至少一个需求类型");
        }
        requirement.setType(defaultType.getCode());
        requirement.setIterationId(null);
        requirement.setCreatorId(creatorId);
        requirement.setStatus(workflowService.resolveInitialStateName(dto.getProjectId(), requirement));
        if (requirement.getOrderNum() == null) {
            requirement.setOrderNum(0);
        }
        requirement.setVersion(0);

        insertRequirementWithGeneratedNo(requirement);

        // PRD: 父需求优先级默认传递至子需求
        // 如果有父需求，子需求继承父需求的优先级
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
        knowledgeDocumentService.syncRequirementAttachments(requirement.getProjectId(), requirement.getId(), dto.getAttachments(), creatorId);
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
            updateWrapper.set("cc_user_ids", dto.getCcUserIds());
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
        if (dto.getDueDate() != null && !Objects.equals(existing.getDueDate(), dto.getDueDate())) {
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
            updateWrapper.set("attachments", dto.getAttachments());
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
            knowledgeDocumentService.syncRequirementAttachments(existing.getProjectId(), existing.getId(), dto.getAttachments(), userId);
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
        RequirementTypeConfig defaultType = requirementConfigService.getDefaultType();
        if (defaultType == null || !StringUtils.hasText(defaultType.getCode())) {
            throw new BusinessException("请先配置至少一个需求类型");
        }
        requirement.setType(defaultType.getCode());
        requirement.setIterationId(null);
        requirement.setCreatorId(creatorId);
        requirement.setStatus(resolveNodeStatusName("DRAFT"));
        requirement.setWorkflowInstanceId(null);
        requirement.setNodeStatus("DRAFT");
        requirement.setIsDraft(true);
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
        knowledgeDocumentService.syncRequirementAttachments(requirement.getProjectId(), requirement.getId(), dto.getAttachments(), creatorId);
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

        Requirement update = new Requirement();
        update.setId(dto.getId());
        update.setVersion(dto.getVersion());
        if (dto.getProjectId() != null) {
            requireProjectSelection(dto.getProjectId());
            Long nextProjectId = normalizeProjectId(dto.getProjectId());
            if (!Objects.equals(normalizeProjectId(existing.getProjectId()), nextProjectId)) {
                ensureProjectCanBeBound(nextProjectId);
            }
            update.setProjectId(nextProjectId);
        }
        if (dto.getParentId() != null) update.setParentId(dto.getParentId());
        if (dto.getTitle() != null) update.setTitle(dto.getTitle());
        if (dto.getDescription() != null) update.setDescription(dto.getDescription());
        if (dto.getPriority() != null) update.setPriority(dto.getPriority());
        if (dto.getAssigneeId() != null) update.setAssigneeId(dto.getAssigneeId());
        if (dto.getCcUserIds() != null) update.setCcUserIds(dto.getCcUserIds());
        if (dto.getModuleId() != null) update.setModuleId(dto.getModuleId());
        if (dto.getStartDate() != null) update.setStartDate(dto.getStartDate());
        if (dto.getDueDate() != null) update.setDueDate(dto.getDueDate());
        if (dto.getEstimatedHours() != null) update.setEstimatedHours(dto.getEstimatedHours());
        if (dto.getAttachments() != null) update.setAttachments(dto.getAttachments());

        int updated = requirementMapper.updateById(update);
        if (updated <= 0) {
            throw new BusinessException(409, "草稿已被他人更新，请刷新后重试");
        }

        recordHistory(dto.getId(), userId, "update", null, "更新草稿");
        if (dto.getAttachments() != null) {
            Long attachmentProjectId = dto.getProjectId() != null ? dto.getProjectId() : existing.getProjectId();
            knowledgeDocumentService.syncRequirementAttachments(attachmentProjectId, existing.getId(), dto.getAttachments(), userId);
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

        WorkflowVersion active = findActiveWorkflowVersion(requirement.getProjectId());
        if (active == null) {
            throw new BusinessException(400, "当前项目未启用工作流");
        }
        WorkflowNode startNode = workflowNodeMapper.selectOne(
                new LambdaQueryWrapper<WorkflowNode>()
                        .eq(WorkflowNode::getWorkflowVersionId, active.getId())
                        .eq(WorkflowNode::getNodeType, "start")
                        .last("LIMIT 1")
        );
        if (startNode == null) {
            throw new BusinessException(400, "工作流缺少开始节点");
        }

        List<WorkflowEdge> edges = workflowEdgeMapper.selectList(
                new LambdaQueryWrapper<WorkflowEdge>()
                        .eq(WorkflowEdge::getWorkflowVersionId, active.getId())
                        .eq(WorkflowEdge::getSourceNodeId, startNode.getNodeId())
        );
        if (edges == null || edges.isEmpty()) {
            return List.of();
        }

        List<NextNodeOptionDTO> options = new ArrayList<>();
        for (WorkflowEdge edge : edges) {
            WorkflowNode node = workflowNodeMapper.selectOne(
                    new LambdaQueryWrapper<WorkflowNode>()
                            .eq(WorkflowNode::getWorkflowVersionId, active.getId())
                            .eq(WorkflowNode::getNodeId, edge.getTargetNodeId())
                            .last("LIMIT 1")
            );
            if (node == null) {
                continue;
            }
            NextNodeOptionDTO opt = new NextNodeOptionDTO();
            opt.setNodeId(node.getNodeId());
            opt.setNodeName(node.getNodeName());
            String nodeStatusCode = resolveNodeStatusCode(node);
            opt.setBindStatusCode(nodeStatusCode);
            opt.setBindStatusName(resolveNodeStatusName(nodeStatusCode));
            opt.setProjectRequired(isProjectRequired(node));
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

        WorkflowVersion active = findActiveWorkflowVersion(requirement.getProjectId());
        if (active == null) {
            throw new BusinessException(400, "当前项目未启用工作流");
        }

        WorkflowNode startNode = workflowNodeMapper.selectOne(
                new LambdaQueryWrapper<WorkflowNode>()
                        .eq(WorkflowNode::getWorkflowVersionId, active.getId())
                        .eq(WorkflowNode::getNodeType, "start")
                        .last("LIMIT 1")
        );
        if (startNode == null) {
            throw new BusinessException(400, "工作流缺少开始节点");
        }

        List<WorkflowEdge> edges = workflowEdgeMapper.selectList(
                new LambdaQueryWrapper<WorkflowEdge>()
                        .eq(WorkflowEdge::getWorkflowVersionId, active.getId())
                        .eq(WorkflowEdge::getSourceNodeId, startNode.getNodeId())
        );
        if (edges == null || edges.isEmpty()) {
            throw new BusinessException(400, "工作流配置异常：开始节点没有后续节点");
        }

        String targetNodeId;
        if (edges.size() == 1) {
            targetNodeId = edges.get(0).getTargetNodeId();
        } else {
            if (!StringUtils.hasText(dto.getNextNodeId())) {
                throw new BusinessException(400, "请选择下一环节");
            }
            boolean ok = edges.stream().anyMatch(e -> dto.getNextNodeId().trim().equals(e.getTargetNodeId()));
            if (!ok) {
                throw new BusinessException(400, "下一环节非法");
            }
            targetNodeId = dto.getNextNodeId().trim();
        }

        Requirement optimistic = new Requirement();
        optimistic.setId(requirementId);
        optimistic.setVersion(dto.getVersion());
        optimistic.setIsDraft(false);
        int updated = requirementMapper.updateById(optimistic);
        if (updated <= 0) {
            throw new BusinessException(409, "需求已被他人处理，请刷新后重试");
        }

        workflowEngineService.initWorkflow(requirementId, active.getId());

        com.demand.system.module.workflow.dto.FlowTransitionRequest request = new com.demand.system.module.workflow.dto.FlowTransitionRequest();
        request.setRequirementId(requirementId);
        request.setToNodeId(targetNodeId);
        request.setProjectId(normalizeProjectId(dto.getProjectId()));
        request.setAction("submit");
        request.setComment(dto.getComment());
        workflowEngineService.transition(request);

        Requirement latest = requirementMapper.selectById(requirementId);
        RequirementVO vo = new RequirementVO();
        if (latest != null) {
            BeanUtils.copyProperties(latest, vo);
            fillUserNames(vo, latest);
        }
        return vo;
    }

    @Override
    public PageResult<RequirementVO> listMyDrafts(RequirementMyListQueryDTO query, Long userId) {
        User user = userMapper.selectById(userId);
        Long departmentId = user != null ? user.getDepartmentId() : null;
        List<String> roleCodes = SecurityUtils.getCurrentUserRoles();
        List<Long> orgIds = resolveOrgScopeIds(user);

        Page<Requirement> page = new Page<>(query.getPageNum(), query.getPageSize());
        var result = requirementMapper.selectMyDrafts(page, userId, departmentId, roleCodes, orgIds, query.getProjectId(), query.getKeyword());
        List<RequirementVO> list = new ArrayList<>();
        for (Requirement r : result.getRecords()) {
            RequirementVO vo = new RequirementVO();
            BeanUtils.copyProperties(r, vo);
            fillUserNames(vo, r);
            list.add(vo);
        }
        return new PageResult<>(list, result.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public PageResult<RequirementVO> listMyPending(RequirementMyListQueryDTO query, Long userId) {
        User user = userMapper.selectById(userId);
        List<String> roleCodes = SecurityUtils.getCurrentUserRoles();
        List<Long> orgIds = resolveOrgScopeIds(user);

        Page<Requirement> page = new Page<>(query.getPageNum(), query.getPageSize());
        var result = requirementMapper.selectMyPending(page, userId, roleCodes, orgIds, query.getProjectId(), query.getKeyword());
        List<RequirementVO> list = new ArrayList<>();
        for (Requirement r : result.getRecords()) {
            RequirementVO vo = new RequirementVO();
            BeanUtils.copyProperties(r, vo);
            fillUserNames(vo, r);
            list.add(vo);
        }
        return new PageResult<>(list, result.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long userId) {
        Requirement requirement = requirementMapper.selectById(id);
        if (requirement == null) {
            throw new BusinessException("需求不存在");
        }

        // 检查权限：只有创建者或admin角色可以删除
        boolean isCreator = requirement.getCreatorId() != null
                && requirement.getCreatorId().equals(userId);
        boolean isAdmin = SecurityUtils.getCurrentUserRoles().contains("admin");

        if (!isCreator && !isAdmin) {
            throw new BusinessException("只有创建者或管理员可以删除需求");
        }

        Long transitionCount = workflowTransitionRecordMapper.selectCount(
                new LambdaQueryWrapper<com.demand.system.module.workflow.entity.WorkflowTransitionRecord>()
                        .eq(com.demand.system.module.workflow.entity.WorkflowTransitionRecord::getRequirementId, id)
        );
        if (transitionCount != null && transitionCount > 0) {
            throw new BusinessException("已流转的需求不能删除");
        }

        requirementMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restore(Long id, Long userId) {
        Requirement existing = requirementMapper.selectById(id);
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

        UpdateWrapper<Requirement> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id).set("deleted_at", null);
        requirementMapper.update(null, wrapper);
    }

    @Override
    public List<RequirementCommentVO> getComments(Long requirementId) {
        ensureRequirementExists(requirementId);
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
    public List<Map<String, Object>> getHistory(Long requirementId) {
        return historyMapper.selectHistoryByRequirement(requirementId);
    }

    @Override
    public List<Map<String, Object>> getChildren(Long parentId) {
        LambdaQueryWrapper<Requirement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Requirement::getParentId, parentId)
                .eq(Requirement::getDeletedAt, 0)
                .orderByAsc(Requirement::getOrderNum);

        List<Requirement> children = requirementMapper.selectList(wrapper);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Requirement r : children) {
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

    private String strVal(Object val) {
        return val != null ? val.toString() : null;
    }

    private String strDate(LocalDate date) {
        return date != null ? date.toString() : null;
    }

    private String strDecimal(BigDecimal decimal) {
        return decimal != null ? decimal.toString() : null;
    }

    private void insertRequirementWithGeneratedNo(Requirement requirement) {
        for (int attempt = 0; attempt < REQUIREMENT_NO_MAX_RETRY; attempt++) {
            requirement.setRequirementNo(generateRequirementNo(LocalDateTime.now()));
            try {
                requirementMapper.insert(requirement);
                return;
            } catch (DuplicateKeyException ex) {
                if (!isRequirementNoDuplicate(ex) || attempt == REQUIREMENT_NO_MAX_RETRY - 1) {
                    throw ex;
                }
            }
        }
        throw new BusinessException("生成需求编号失败，请稍后重试");
    }

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

    private WorkflowVersion findActiveWorkflowVersion(Long projectId) {
        return workflowVersionResolver.findActiveVersion(normalizeProjectId(projectId)).orElse(null);
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

    private List<Long> resolveOrgScopeIds(User user) {
        if (user == null) {
            return List.of();
        }
        Long root = user.getOrgId() != null ? user.getOrgId() : (user.getDepartmentId() != null ? user.getDepartmentId() : user.getRegionId());
        if (root == null) {
            return List.of();
        }
        return sysOrgService.getDescendantIds(root);
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
                vo.setCreatorName(creator.getRealName());
            }
        }
        if (r.getAssigneeId() != null) {
            User assignee = userMapper.selectById(r.getAssigneeId());
            if (assignee != null) {
                vo.setAssigneeName(assignee.getRealName());
            }
        }
        if (r.getOpsFollowId() != null) {
            User opsFollow = userMapper.selectById(r.getOpsFollowId());
            if (opsFollow != null) {
                vo.setOpsFollowName(opsFollow.getRealName());
            }
        }
        if (r.getMaintFollowId() != null) {
            User maintFollow = userMapper.selectById(r.getMaintFollowId());
            if (maintFollow != null) {
                vo.setMaintFollowName(maintFollow.getRealName());
            }
        }
        if (r.getDepartmentId() != null) {
            SysOrgVO dept = sysOrgService.getDetail(r.getDepartmentId());
            if (dept != null) {
                vo.setDepartmentName(dept.getName());
            }
        }
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
