package com.demand.system.module.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.workflow.dto.WorkflowApprovalDTO;
import com.demand.system.module.workflow.dto.WorkflowConfigDTO;
import com.demand.system.module.workflow.dto.WorkflowEdgeDTO;
import com.demand.system.module.workflow.dto.WorkflowNodeDTO;
import com.demand.system.module.workflow.dto.WorkflowVersionActivationDTO;
import com.demand.system.module.workflow.dto.WorkflowVersionMetaUpdateDTO;
import com.demand.system.module.workflow.dto.WorkflowVersionDTO;
import com.demand.system.module.workflow.engine.WorkflowVersionResolver;
import com.demand.system.module.workflow.entity.WorkflowApproval;
import com.demand.system.module.workflow.entity.WorkflowEdge;
import com.demand.system.module.workflow.entity.WorkflowInstance;
import com.demand.system.module.workflow.entity.WorkflowNode;
import com.demand.system.module.workflow.entity.WorkflowNodePermission;
import com.demand.system.module.workflow.entity.WorkflowVersion;
import com.demand.system.module.workflow.mapper.WorkflowApprovalMapper;
import com.demand.system.module.workflow.mapper.WorkflowEdgeMapper;
import com.demand.system.module.workflow.mapper.WorkflowInstanceMapper;
import com.demand.system.module.workflow.mapper.WorkflowNodeMapper;
import com.demand.system.module.workflow.mapper.WorkflowNodePermissionMapper;
import com.demand.system.module.workflow.mapper.WorkflowVersionMapper;
import com.demand.system.module.workflow.mapper.WorkflowDefinitionMapper;
import com.demand.system.module.workflow.entity.WorkflowDefinition;
import com.demand.system.module.workflow.service.WorkflowConfigService;
import com.demand.system.module.workflow.dto.WorkflowValidationIssue;
import com.demand.system.module.workflow.dto.WorkflowValidationReport;
import com.demand.system.module.workflow.engine.WorkflowGraphCompiler;
import com.demand.system.module.workflow.engine.WorkflowGraphValidator;
import com.demand.system.module.workflow.service.WorkflowActivationService;
import com.demand.system.module.workflow.support.WorkflowVersionUtils;
import com.demand.system.module.knowledge.entity.KnowledgeBase;
import com.demand.system.module.knowledge.mapper.KnowledgeBaseMapper;
import com.demand.system.module.project.mapper.ProjectMapper;
import com.demand.system.module.project.entity.Project;
import com.demand.system.module.auth.mapper.SysUserMapper;
import com.demand.system.module.auth.entity.SysUser;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


@Service
public class WorkflowConfigServiceImpl implements WorkflowConfigService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WorkflowConfigServiceImpl.class);
    private static final String GLOBAL_WORKFLOW_NAME = "全局流程";

    private final WorkflowVersionMapper workflowVersionMapper;
    private final WorkflowDefinitionMapper workflowDefinitionMapper;
    private final WorkflowNodeMapper workflowNodeMapper;
    private final WorkflowEdgeMapper workflowEdgeMapper;
    private final WorkflowApprovalMapper workflowApprovalMapper;
    private final WorkflowInstanceMapper workflowInstanceMapper;
    private final WorkflowNodePermissionMapper workflowNodePermissionMapper;
    private final ProjectMapper projectMapper;
    private final SysUserMapper sysUserMapper;
    private final WorkflowGraphValidator workflowGraphValidator;
    private final WorkflowGraphCompiler workflowGraphCompiler;
    private final WorkflowActivationService workflowActivationService;
    private final KnowledgeBaseMapper knowledgeBaseMapper;

    public WorkflowConfigServiceImpl(WorkflowVersionMapper workflowVersionMapper, WorkflowDefinitionMapper workflowDefinitionMapper,
                                   WorkflowNodeMapper workflowNodeMapper,
                                   WorkflowEdgeMapper workflowEdgeMapper, WorkflowApprovalMapper workflowApprovalMapper,
                                   WorkflowInstanceMapper workflowInstanceMapper, WorkflowNodePermissionMapper workflowNodePermissionMapper,
                                   ProjectMapper projectMapper, SysUserMapper sysUserMapper,
                                   WorkflowGraphValidator workflowGraphValidator, WorkflowGraphCompiler workflowGraphCompiler,
                                   WorkflowActivationService workflowActivationService,
                                   KnowledgeBaseMapper knowledgeBaseMapper) {
        this.workflowVersionMapper = workflowVersionMapper;
        this.workflowDefinitionMapper = workflowDefinitionMapper;
        this.workflowNodeMapper = workflowNodeMapper;
        this.workflowEdgeMapper = workflowEdgeMapper;
        this.workflowApprovalMapper = workflowApprovalMapper;
        this.workflowInstanceMapper = workflowInstanceMapper;
        this.workflowNodePermissionMapper = workflowNodePermissionMapper;
        this.projectMapper = projectMapper;
        this.sysUserMapper = sysUserMapper;
        this.workflowGraphValidator = workflowGraphValidator;
        this.workflowGraphCompiler = workflowGraphCompiler;
        this.workflowActivationService = workflowActivationService;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
    }

    @Override
    public WorkflowConfigDTO getWorkflowConfig(Long projectId) {
        Long normalizedProjectId = normalizeProjectId(projectId);
        // 获取当前激活的版本
        LambdaQueryWrapper<WorkflowVersion> versionQuery = new LambdaQueryWrapper<>();
        versionQuery.eq(WorkflowVersion::getProjectId, normalizedProjectId)
                .eq(WorkflowVersion::getIsActive, 1);

        WorkflowVersion activeVersion = workflowVersionMapper.selectList(versionQuery).stream()
                .sorted(WorkflowVersionUtils.byVersionDesc())
                .findFirst()
                .orElse(null);

        if (activeVersion == null) {
            // 如果没有激活版本，返回空配置
            WorkflowConfigDTO configDTO = new WorkflowConfigDTO();
            configDTO.setNodes(new ArrayList<>());
            configDTO.setEdges(new ArrayList<>());
            return configDTO;
        }

        return loadVersionConfig(activeVersion.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowVersionDTO saveWorkflowConfig(Long projectId, WorkflowConfigDTO configDTO) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException("用户未登录");
        }

        // 保存前做连通性和基本结构校验，避免保存无效流程
        validateConfigStructure(configDTO);

        Long normalizedProjectId = normalizeProjectId(projectId);

        // 场景A：有 versionId → 编辑已有版本
        if (configDTO.getVersionId() != null) {
            WorkflowVersion existingVersion = workflowVersionMapper.selectById(configDTO.getVersionId());
            if (existingVersion == null) {
                throw new BusinessException("版本不存在");
            }
            if (!existingVersion.getProjectId().equals(normalizedProjectId)) {
                throw new BusinessException("该版本不属于当前项目");
            }

            String status = existingVersion.getActivationStatus();
            // pending 状态不允许编辑（审核中）
            if ("pending".equals(status)) {
                throw new BusinessException("该版本已提交审核，请等待审核完成后再保存");
            }
            if (existingVersion.getIsActive() != null && existingVersion.getIsActive() == 1) {
                throw new BusinessException("启用中的版本不支持编辑");
            }
            if (hasPendingApproval(existingVersion.getId())) {
                throw new BusinessException("该版本已提交审核，请等待审核完成后再保存");
            }
            // draft / approved / inactive / rejected 状态都允许编辑保存

            // 记录旧的 hash 用于后续对比
            String oldRuntimeHash = existingVersion.getRuntimeHash();
            String oldConfigHash = existingVersion.getConfigHash();

            // 如有版本号或名称变更，先校验
            if (StringUtils.hasText(configDTO.getVersion()) || StringUtils.hasText(configDTO.getVersionName())) {
                String targetVersion = StringUtils.hasText(configDTO.getVersion())
                        ? normalizeVersion(configDTO.getVersion())
                        : existingVersion.getVersion();
                String targetName = StringUtils.hasText(configDTO.getVersionName())
                        ? normalizeVersionName(configDTO.getVersionName())
                        : existingVersion.getName();
                validateVersionMeta(existingVersion.getProjectId(), existingVersion.getId(), targetVersion, targetName);
                existingVersion.setVersion(targetVersion);
                existingVersion.setName(targetName);
                existingVersion.setWorkflowDefinitionId(resolveOrCreateDefinition(
                        existingVersion.getProjectId(), targetName, existingVersion.getCreatorId()));
            }

            // 删除旧节点和连线
            deleteVersionConfig(existingVersion.getId());

            // 保存新节点
            saveNodes(configDTO.getNodes(), existingVersion.getId());
            // 保存新连线
            saveEdges(configDTO.getEdges(), existingVersion.getId());

            // 重新编译
            List<WorkflowNode> savedNodes = workflowNodeMapper.selectList(new LambdaQueryWrapper<WorkflowNode>()
                    .eq(WorkflowNode::getWorkflowVersionId, existingVersion.getId()));
            List<WorkflowEdge> savedEdges = workflowEdgeMapper.selectList(new LambdaQueryWrapper<WorkflowEdge>()
                    .eq(WorkflowEdge::getWorkflowVersionId, existingVersion.getId()));
            List<WorkflowValidationIssue> issuesA = workflowGraphValidator.validate(savedNodes, savedEdges);
            WorkflowGraphCompiler.CompiledWorkflow compiled = workflowGraphCompiler.compile(existingVersion.getId(), savedNodes, savedEdges);
            existingVersion.setDefinition(compiled.definitionJson());
            existingVersion.setRuntimeHash(compiled.runtimeHash());
            existingVersion.setConfigHash(compiled.configHash());

            // 根据 hash 对比决定 activationStatus
            // approved / inactive / rejected 状态编辑后，如配置变更则回退为 draft
            if ("approved".equals(status) || "inactive".equals(status) || "rejected".equals(status)) {
                boolean configChanged = !compiled.runtimeHash().equals(oldRuntimeHash)
                                     || (oldConfigHash == null || !compiled.configHash().equals(oldConfigHash));
                if (configChanged) {
                    existingVersion.setActivationStatus("draft");
                    log.info("非启用版本配置变更，回退为草稿状态，versionId={}, 原状态={}", existingVersion.getId(), status);
                } else {
                    log.info("非启用版本仅修改元数据，保持原状态，versionId={}, status={}", existingVersion.getId(), status);
                }
            }

            // 更新知识库绑定（允许清空）
            if (configDTO.getKnowledgeBaseId() != null) {
                KnowledgeBase kb = knowledgeBaseMapper.selectById(configDTO.getKnowledgeBaseId());
                if (kb == null) {
                    throw new BusinessException("指定的知识库不存在");
                }
                existingVersion.setKnowledgeBaseId(configDTO.getKnowledgeBaseId());
            } else {
                existingVersion.setKnowledgeBaseId(null);
            }

            existingVersion.setUpdatedAt(LocalDateTime.now());
            workflowVersionMapper.updateById(existingVersion);

            log.info("更新工作流版本成功，projectId={}, versionId={}, status={}", normalizedProjectId, existingVersion.getId(), existingVersion.getActivationStatus());
            WorkflowVersionDTO dto = toVersionDTO(existingVersion);
            dto.setValidationIssues(issuesA);
            return dto;
        }

        // 场景B：无 versionId → 新建草稿版本
        // 先校验版本号冲突（支持语义等价检测，如 1 vs 1.0.0）
        String targetVersion = StringUtils.hasText(configDTO.getVersion())
                ? normalizeVersion(configDTO.getVersion())
                : null;
        String targetName = StringUtils.hasText(configDTO.getVersionName())
                ? normalizeVersionName(configDTO.getVersionName())
                : null;
        validateVersionMeta(normalizedProjectId, null, targetVersion, targetName);

        // 创建新草稿版本
        WorkflowVersion draftVersion = createDraftVersion(normalizedProjectId, currentUserId, targetVersion, targetName);

        // 设置知识库绑定
        if (configDTO.getKnowledgeBaseId() != null) {
            KnowledgeBase kb = knowledgeBaseMapper.selectById(configDTO.getKnowledgeBaseId());
            if (kb == null) {
                throw new BusinessException("指定的知识库不存在");
            }
            draftVersion.setKnowledgeBaseId(configDTO.getKnowledgeBaseId());
            workflowVersionMapper.updateById(draftVersion);
        }

        // 保存节点
        saveNodes(configDTO.getNodes(), draftVersion.getId());
        // 保存连线
        saveEdges(configDTO.getEdges(), draftVersion.getId());

        // 编译并更新版本
        List<WorkflowNode> savedNodes = workflowNodeMapper.selectList(new LambdaQueryWrapper<WorkflowNode>()
                .eq(WorkflowNode::getWorkflowVersionId, draftVersion.getId()));
        List<WorkflowEdge> savedEdges = workflowEdgeMapper.selectList(new LambdaQueryWrapper<WorkflowEdge>()
                .eq(WorkflowEdge::getWorkflowVersionId, draftVersion.getId()));
        List<WorkflowValidationIssue> issuesB = workflowGraphValidator.validate(savedNodes, savedEdges);
        WorkflowGraphCompiler.CompiledWorkflow compiled = workflowGraphCompiler.compile(draftVersion.getId(), savedNodes, savedEdges);
        draftVersion.setDefinition(compiled.definitionJson());
        draftVersion.setRuntimeHash(compiled.runtimeHash());
        draftVersion.setConfigHash(compiled.configHash());
        draftVersion.setUpdatedAt(LocalDateTime.now());
        workflowVersionMapper.updateById(draftVersion);

        log.info("新建工作流草稿成功，projectId={}, versionId={}, version={}", normalizedProjectId, draftVersion.getId(), draftVersion.getVersion());
        WorkflowVersionDTO dto = toVersionDTO(draftVersion);
        dto.setValidationIssues(issuesB);
        return dto;
    }

    /**
     * 保存节点列表（带版本前缀）
     */
    private void saveNodes(List<WorkflowNodeDTO> nodes, Long versionId) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        String prefix = "v" + versionId + "_";
        for (WorkflowNodeDTO nodeDTO : nodes) {
            WorkflowNode node = new WorkflowNode();
            BeanUtils.copyProperties(nodeDTO, node);
            node.setWorkflowVersionId(versionId);
            if (StringUtils.hasText(node.getNodeId()) && !node.getNodeId().startsWith(prefix)) {
                node.setNodeId(prefix + node.getNodeId());
            }
            normalizeNodeAssigneeFields(node);
            workflowNodeMapper.insert(node);
        }
    }

    private void normalizeNodeAssigneeFields(WorkflowNode node) {
        if (node == null) {
            return;
        }
        Map<String, Object> properties = node.getProperties();
        if (properties != null) {
            if (!StringUtils.hasText(node.getAssigneeType())) {
                Object assigneeType = properties.get("assigneeType");
                if (assigneeType != null) {
                    node.setAssigneeType(String.valueOf(assigneeType));
                }
            }
            if (node.getAssigneeRoleId() == null) {
                node.setAssigneeRoleId(toInteger(properties.get("assigneeRoleId")));
            }
            if (node.getAssigneeRoleGroupId() == null) {
                node.setAssigneeRoleGroupId(toLong(properties.get("assigneeRoleGroupId")));
            }
            if (node.getAssigneeOrgId() == null) {
                node.setAssigneeOrgId(toLong(properties.get("assigneeOrgId")));
            }
            if (node.getAssigneeUserIds() == null) {
                node.setAssigneeUserIds(toLongList(properties.get("assigneeUserIds")));
            }
        }

        String assigneeType = node.getAssigneeType();
        if (!StringUtils.hasText(assigneeType)) {
            return;
        }
        if ("CREATOR".equals(assigneeType) || "PREV_APPROVER".equals(assigneeType)) {
            node.setAssigneeRoleId(null);
            node.setAssigneeRoleGroupId(null);
            node.setAssigneeOrgId(null);
            node.setAssigneeUserIds(null);
        } else if ("SPECIFIED_USER".equals(assigneeType)) {
            node.setAssigneeRoleId(null);
            node.setAssigneeRoleGroupId(null);
            node.setAssigneeOrgId(null);
        } else if ("SPECIFIED_ROLE".equals(assigneeType)) {
            node.setAssigneeRoleGroupId(null);
            node.setAssigneeOrgId(null);
            node.setAssigneeUserIds(null);
        } else if ("SPECIFIED_ROLE_GROUP".equals(assigneeType)) {
            node.setAssigneeRoleId(null);
            node.setAssigneeOrgId(null);
            node.setAssigneeUserIds(null);
        } else if ("SPECIFIED_ORG".equals(assigneeType)) {
            node.setAssigneeRoleId(null);
            node.setAssigneeRoleGroupId(null);
            node.setAssigneeUserIds(null);
        }
    }

    private Integer toInteger(Object value) {
        Long longValue = toLong(value);
        if (longValue == null) {
            return null;
        }
        return longValue.intValue();
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private List<Long> toLongList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return null;
        }
        List<Long> ids = new ArrayList<>();
        for (Object item : rawList) {
            Long id = toLong(item);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    /**
     * 保存连线列表（带版本前缀）
     */
    private void saveEdges(List<WorkflowEdgeDTO> edges, Long versionId) {
        if (edges == null || edges.isEmpty()) {
            return;
        }
        String prefix = "v" + versionId + "_";
        for (WorkflowEdgeDTO edgeDTO : edges) {
            WorkflowEdge edge = new WorkflowEdge();
            BeanUtils.copyProperties(edgeDTO, edge);
            edge.setWorkflowVersionId(versionId);
            if (StringUtils.hasText(edge.getSourceNodeId()) && !edge.getSourceNodeId().startsWith(prefix)) {
                edge.setSourceNodeId(prefix + edge.getSourceNodeId());
            }
            if (StringUtils.hasText(edge.getTargetNodeId()) && !edge.getTargetNodeId().startsWith(prefix)
                    && !isTerminalStatus(edge.getTargetNodeId())) {
                edge.setTargetNodeId(prefix + edge.getTargetNodeId());
            }
            workflowEdgeMapper.insert(edge);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitForApproval(Long projectId, Long versionId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException("用户未登录");
        }

        Long normalizedProjectId = normalizeProjectId(projectId);
        WorkflowVersion draftVersion = resolveSubmissionVersion(normalizedProjectId, versionId, "没有可提交的版本");

        if (draftVersion == null) {
            throw new BusinessException("没有可提交的版本");
        }

        // 仅 draft / inactive / rejected / approved（未启用）状态可提交审核
        String status = draftVersion.getActivationStatus();
        if ("pending".equals(status)) {
            throw new BusinessException("该版本已提交审核，请等待审核完成");
        }
        if ("active".equals(status)) {
            throw new BusinessException("启用中的版本无需提交审核");
        }

        if (hasPendingApproval(draftVersion.getId())) {
            throw new BusinessException("该版本已提交审核，请勿重复提交");
        }

        List<WorkflowNode> nodes = workflowNodeMapper.selectList(new LambdaQueryWrapper<WorkflowNode>()
                .eq(WorkflowNode::getWorkflowVersionId, draftVersion.getId()));
        List<WorkflowEdge> edges = workflowEdgeMapper.selectList(new LambdaQueryWrapper<WorkflowEdge>()
                .eq(WorkflowEdge::getWorkflowVersionId, draftVersion.getId()));
        WorkflowValidationReport validationReport = buildValidationReport(draftVersion, nodes, edges, false);
        // 仅错误阻断提交；warning/info 已由前端展示并允许用户确认后继续。
        if (validationReport.getErrorCount() > 0) {
            throw new BusinessException(400, "工作流配置存在异常，请修复后再提交审核", validationReport);
        }

        draftVersion.setActivationStatus("pending");
        draftVersion.setUpdatedAt(LocalDateTime.now());
        workflowVersionMapper.updateById(draftVersion);

        // 创建审核记录
        WorkflowApproval approval = new WorkflowApproval();
        approval.setWorkflowVersionId(draftVersion.getId());
        approval.setSubmitterId(currentUserId);
        approval.setStatus("pending");
        approval.setSubmittedAt(LocalDateTime.now());
        workflowApprovalMapper.insert(approval);

        log.info("提交工作流审核成功，projectId={}, versionId={}, 原状态={}", normalizedProjectId, draftVersion.getId(), status);
    }

    @Override
    public List<WorkflowVersionDTO> getVersionHistory(Long projectId) {
        Long normalizedProjectId = normalizeProjectId(projectId);
        LambdaQueryWrapper<WorkflowVersion> query = new LambdaQueryWrapper<>();
        query.eq(WorkflowVersion::getProjectId, normalizedProjectId);

        List<WorkflowVersion> versions = workflowVersionMapper.selectList(query).stream()
                .sorted(WorkflowVersionUtils.byVersionDesc())
                .collect(Collectors.toList());

        return versions.stream()
                .map(this::toVersionDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<WorkflowVersionDTO> listActiveVersions() {
        return workflowVersionMapper.selectList(new LambdaQueryWrapper<WorkflowVersion>()
                        .eq(WorkflowVersion::getIsActive, 1)
                        .eq(WorkflowVersion::getActivationStatus, "active"))
                .stream()
                .sorted(WorkflowVersionUtils.byVersionDesc())
                .map(this::toVersionDTO)
                .collect(Collectors.toList());
    }

    @Override
    public WorkflowVersionDTO getVersionConfig(Long versionId) {
        WorkflowVersion version = workflowVersionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException("版本不存在");
        }

        WorkflowVersionDTO dto = toVersionDTO(version);

        // 加载配置
        WorkflowConfigDTO config = loadVersionConfig(versionId);
        dto.setConfig(config);

        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowVersionDTO updateVersionMeta(Long versionId, WorkflowVersionMetaUpdateDTO updateDTO) {
        WorkflowVersion version = workflowVersionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException("版本不存在");
        }

        if (hasPendingApproval(versionId)) {
            throw new BusinessException("该版本已提交审核，暂不支持修改版本信息");
        }

        String targetVersion = normalizeVersion(updateDTO.getVersion());
        String targetName = normalizeVersionName(updateDTO.getName());
        validateVersionMeta(version.getProjectId(), versionId, targetVersion, targetName);

        version.setVersion(targetVersion);
        version.setName(targetName);
        version.setWorkflowDefinitionId(resolveOrCreateDefinition(
                version.getProjectId(), targetName, version.getCreatorId()));

        // 更新知识库绑定关系
        if (updateDTO.getKnowledgeBaseId() != null) {
            // 校验知识库存在且可用
            KnowledgeBase kb = knowledgeBaseMapper.selectById(updateDTO.getKnowledgeBaseId());
            if (kb == null) {
                throw new BusinessException("指定的知识库不存在");
            }
            version.setKnowledgeBaseId(updateDTO.getKnowledgeBaseId());
        } else {
            // 允许清空绑定
            version.setKnowledgeBaseId(null);
        }

        // 更新评价开关
        if (updateDTO.getApprovalEvaluationEnabled() != null) {
            version.setApprovalEvaluationEnabled(updateDTO.getApprovalEvaluationEnabled());
        }

        workflowVersionMapper.updateById(version);

        return toVersionDTO(version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowVersionDTO updateVersionActivation(Long versionId, WorkflowVersionActivationDTO activationDTO) {
        WorkflowVersion version = workflowVersionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException("版本不存在");
        }

        boolean active = Boolean.TRUE.equals(activationDTO.getActive());
        if (active) {
            return workflowActivationService.activate(versionId);
        }
        return workflowActivationService.deactivate(versionId);
    }

    @Override
    public WorkflowValidationReport validateLatestDraft(Long projectId, Long versionId) {
        Long normalizedProjectId = normalizeProjectId(projectId);
        WorkflowVersion draftVersion = resolveSubmissionVersion(normalizedProjectId, versionId, "没有可校验的版本");
        List<WorkflowNode> nodes = workflowNodeMapper.selectList(new LambdaQueryWrapper<WorkflowNode>()
                .eq(WorkflowNode::getWorkflowVersionId, draftVersion.getId()));
        List<WorkflowEdge> edges = workflowEdgeMapper.selectList(new LambdaQueryWrapper<WorkflowEdge>()
                .eq(WorkflowEdge::getWorkflowVersionId, draftVersion.getId()));
        return buildValidationReport(draftVersion, nodes, edges, false);
    }

    @Override
    public WorkflowValidationReport validateVersionReport(Long versionId) {
        WorkflowVersion version = workflowVersionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException("版本不存在");
        }
        List<WorkflowNode> nodes = workflowNodeMapper.selectList(new LambdaQueryWrapper<WorkflowNode>()
                .eq(WorkflowNode::getWorkflowVersionId, versionId));
        List<WorkflowEdge> edges = workflowEdgeMapper.selectList(new LambdaQueryWrapper<WorkflowEdge>()
                .eq(WorkflowEdge::getWorkflowVersionId, versionId));
        return buildValidationReport(version, nodes, edges, true);
    }

    @Override
    public List<WorkflowValidationIssue> validateVersion(Long versionId) {
        return validateVersionReport(versionId).getIssues();
    }

    @Override
    public WorkflowValidationReport validateConfig(WorkflowConfigDTO configDTO) {
        // 基本结构校验，无效结构抛异常
        validateConfigStructure(configDTO);

        // 将 DTO 转换为临时实体以进行校验
        List<WorkflowNode> tempNodes = configDTO.getNodes() == null ? List.of() :
            configDTO.getNodes().stream().map(dto -> {
                WorkflowNode node = new WorkflowNode();
                node.setNodeId(dto.getNodeId());
                node.setNodeType(dto.getNodeType());
                node.setNodeName(dto.getNodeName());
                node.setPositionX(dto.getPositionX());
                node.setPositionY(dto.getPositionY());
                node.setAssigneeType(dto.getAssigneeType());
                node.setAssigneeRoleId(dto.getAssigneeRoleId());
                node.setAssigneeRoleGroupId(dto.getAssigneeRoleGroupId());
                node.setAssigneeOrgId(dto.getAssigneeOrgId());
                node.setAssigneeUserIds(dto.getAssigneeUserIds());
                node.setTimeoutHours(dto.getTimeoutHours());
                node.setTimeoutAction(dto.getTimeoutAction());
                node.setProperties(dto.getProperties());
                return node;
            }).toList();

        List<WorkflowEdge> tempEdges = configDTO.getEdges() == null ? List.of() :
            configDTO.getEdges().stream().map(dto -> {
                WorkflowEdge edge = new WorkflowEdge();
                edge.setEdgeId(dto.getEdgeId());
                edge.setSourceNodeId(dto.getSourceNodeId());
                edge.setTargetNodeId(dto.getTargetNodeId());
                edge.setLabel(dto.getLabel());
                edge.setCondition(dto.getCondition());
                edge.setProperties(dto.getProperties());
                return edge;
            }).toList();

        List<WorkflowValidationIssue> issues = workflowGraphValidator.validate(tempNodes, tempEdges);
        WorkflowValidationReport report = new WorkflowValidationReport();
        report.setVersion(StringUtils.hasText(configDTO.getVersion()) ? configDTO.getVersion() : null);
        report.setVersionName(StringUtils.hasText(configDTO.getVersionName()) ? configDTO.getVersionName() : null);
        report.setValidatedAt(LocalDateTime.now());
        report.setIssues(issues);
        report.computeSummary();
        return report;
    }

    private WorkflowValidationReport buildValidationReport(WorkflowVersion version, List<WorkflowNode> nodes,
                                                           List<WorkflowEdge> edges, boolean forActivation) {
        List<WorkflowValidationIssue> issues = forActivation
                ? workflowGraphValidator.validateForActivation(nodes, edges, version.getProjectId())
                : workflowGraphValidator.validate(nodes, edges);
        WorkflowValidationReport report = new WorkflowValidationReport();
        report.setVersionId(version.getId());
        report.setVersionName(version.getName());
        report.setVersion(version.getVersion());
        report.setValidatedAt(LocalDateTime.now());
        report.setIssues(issues);
        report.computeSummary();
        return report;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteVersion(Long versionId) {
        WorkflowVersion version = workflowVersionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException("版本不存在");
        }
        if (version.getIsActive() != null && version.getIsActive() == 1) {
            throw new BusinessException("启用中的工作流不能删除，请先停用");
        }

        Long instanceCount = workflowInstanceMapper.selectCount(new LambdaQueryWrapper<WorkflowInstance>()
                .eq(WorkflowInstance::getWorkflowVersionId, versionId));
        if (instanceCount != null && instanceCount > 0) {
            throw new BusinessException("该工作流已被历史流程实例引用，暂不支持删除");
        }

        workflowApprovalMapper.delete(new LambdaQueryWrapper<WorkflowApproval>()
                .eq(WorkflowApproval::getWorkflowVersionId, versionId));
        workflowNodePermissionMapper.delete(new LambdaQueryWrapper<WorkflowNodePermission>()
                .eq(WorkflowNodePermission::getWorkflowVersionId, versionId));
        deleteVersionConfig(versionId);
        workflowVersionMapper.deleteById(versionId);
    }

    @Override
    public List<WorkflowApprovalDTO> getPendingApprovals() {
        LambdaQueryWrapper<WorkflowApproval> query = new LambdaQueryWrapper<>();
        query.eq(WorkflowApproval::getStatus, "pending")
                .orderByDesc(WorkflowApproval::getSubmittedAt);

        List<WorkflowApproval> approvals = workflowApprovalMapper.selectList(query);
        return approvals.stream()
                .map(this::toApprovalDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<WorkflowApprovalDTO> getWorkflowApprovals() {
        LambdaQueryWrapper<WorkflowApproval> query = new LambdaQueryWrapper<>();
        query.orderByDesc(WorkflowApproval::getSubmittedAt);
        List<WorkflowApproval> approvals = workflowApprovalMapper.selectList(query);
        return approvals.stream()
                .map(this::toApprovalDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveWorkflow(Long approvalId, String comment) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException("用户未登录");
        }

        WorkflowApproval approval = workflowApprovalMapper.selectById(approvalId);
        if (approval == null) {
            throw new BusinessException("审核记录不存在");
        }

        if (!"pending".equals(approval.getStatus())) {
            throw new BusinessException("该审核已处理");
        }

        // 更新审核记录
        approval.setStatus("approved");
        approval.setApproverId(currentUserId);
        approval.setComment(normalizeComment(comment));
        approval.setApprovedAt(LocalDateTime.now());
        workflowApprovalMapper.updateById(approval);

        WorkflowVersion version = workflowVersionMapper.selectById(approval.getWorkflowVersionId());
        if (version != null) {
            version.setActivationStatus("approved");
            version.setUpdatedAt(LocalDateTime.now());
            workflowVersionMapper.updateById(version);
            log.info("审核通过工作流版本，versionId={}, projectId={}，请手动启用后生效", version.getId(), version.getProjectId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectWorkflow(Long approvalId, String comment) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException("用户未登录");
        }

        String normalizedComment = normalizeComment(comment);
        if (normalizedComment == null) {
            throw new BusinessException("请输入拒绝原因");
        }

        WorkflowApproval approval = workflowApprovalMapper.selectById(approvalId);
        if (approval == null) {
            throw new BusinessException("审核记录不存在");
        }

        if (!"pending".equals(approval.getStatus())) {
            throw new BusinessException("该审核已处理");
        }

        // 更新审核记录
        approval.setStatus("rejected");
        approval.setApproverId(currentUserId);
        approval.setComment(normalizedComment);
        approval.setApprovedAt(LocalDateTime.now());
        workflowApprovalMapper.updateById(approval);

        WorkflowVersion version = workflowVersionMapper.selectById(approval.getWorkflowVersionId());
        if (version != null) {
            version.setActivationStatus("draft");
            version.setUpdatedAt(LocalDateTime.now());
            workflowVersionMapper.updateById(version);
        }

        log.info("审核拒绝工作流版本，approvalId={}, versionId={}", approvalId, approval.getWorkflowVersionId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteApproval(Long approvalId) {
        WorkflowApproval approval = workflowApprovalMapper.selectById(approvalId);
        if (approval == null) {
            throw new BusinessException("审核记录不存在");
        }

        // 如果删除的是待审核记录，需同步将版本状态回退为 draft
        if ("pending".equals(approval.getStatus())) {
            WorkflowVersion version = workflowVersionMapper.selectById(approval.getWorkflowVersionId());
            if (version != null && "pending".equals(version.getActivationStatus())) {
                // 检查该版本是否还有其他待审核记录
                LambdaQueryWrapper<WorkflowApproval> query = new LambdaQueryWrapper<>();
                query.eq(WorkflowApproval::getWorkflowVersionId, approval.getWorkflowVersionId())
                        .eq(WorkflowApproval::getStatus, "pending")
                        .ne(WorkflowApproval::getId, approvalId);
                long remainingPending = workflowApprovalMapper.selectCount(query);
                if (remainingPending == 0) {
                    version.setActivationStatus("draft");
                    workflowVersionMapper.updateById(version);
                }
            }
        }

        workflowApprovalMapper.deleteById(approvalId);
        log.info("删除审核记录，approvalId={}", approvalId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearAllApprovals() {
        // 将所有 pending 状态的版本回退为 draft
        LambdaQueryWrapper<WorkflowApproval> pendingQuery = new LambdaQueryWrapper<>();
        pendingQuery.eq(WorkflowApproval::getStatus, "pending");
        List<WorkflowApproval> pendingApprovals = workflowApprovalMapper.selectList(pendingQuery);

        Set<Long> processedVersionIds = new HashSet<>();
        for (WorkflowApproval approval : pendingApprovals) {
            Long versionId = approval.getWorkflowVersionId();
            if (versionId != null && !processedVersionIds.contains(versionId)) {
                WorkflowVersion version = workflowVersionMapper.selectById(versionId);
                if (version != null && "pending".equals(version.getActivationStatus())) {
                    version.setActivationStatus("draft");
                    workflowVersionMapper.updateById(version);
                }
                processedVersionIds.add(versionId);
            }
        }

        // 清空全部审核记录
        workflowApprovalMapper.delete(new LambdaQueryWrapper<>());
        log.info("清空全部审核记录，共处理 {} 条待审核记录关联的版本状态回退", pendingApprovals.size());
    }

    // ========== 私有方法 ==========

    private WorkflowConfigDTO loadVersionConfig(Long versionId) {
        WorkflowConfigDTO configDTO = new WorkflowConfigDTO();

        // 加载节点
        LambdaQueryWrapper<WorkflowNode> nodeQuery = new LambdaQueryWrapper<>();
        nodeQuery.eq(WorkflowNode::getWorkflowVersionId, versionId);
        List<WorkflowNode> nodes = workflowNodeMapper.selectList(nodeQuery);

        List<WorkflowNodeDTO> nodeDTOs = nodes.stream().map(node -> {
            WorkflowNodeDTO dto = new WorkflowNodeDTO();
            BeanUtils.copyProperties(node, dto);
            return dto;
        }).collect(Collectors.toList());

        configDTO.setNodes(nodeDTOs);

        // 加载连线
        LambdaQueryWrapper<WorkflowEdge> edgeQuery = new LambdaQueryWrapper<>();
        edgeQuery.eq(WorkflowEdge::getWorkflowVersionId, versionId);
        List<WorkflowEdge> edges = workflowEdgeMapper.selectList(edgeQuery);

        List<WorkflowEdgeDTO> edgeDTOs = edges.stream().map(edge -> {
            WorkflowEdgeDTO dto = new WorkflowEdgeDTO();
            BeanUtils.copyProperties(edge, dto);
            return dto;
        }).collect(Collectors.toList());

        configDTO.setEdges(edgeDTOs);

        return configDTO;
    }

    private void deleteVersionConfig(Long versionId) {
        // 删除节点
        LambdaQueryWrapper<WorkflowNode> nodeQuery = new LambdaQueryWrapper<>();
        nodeQuery.eq(WorkflowNode::getWorkflowVersionId, versionId);
        workflowNodeMapper.delete(nodeQuery);

        // 删除连线
        LambdaQueryWrapper<WorkflowEdge> edgeQuery = new LambdaQueryWrapper<>();
        edgeQuery.eq(WorkflowEdge::getWorkflowVersionId, versionId);
        workflowEdgeMapper.delete(edgeQuery);
    }

    /**
     * 判断节点 ID 是否为终止状态字符串（如 cancelled/accepted/rejected），
     * 这些值不应加版本前缀。
     */
    private boolean isTerminalStatus(String nodeId) {
        if (nodeId == null) {
            return false;
        }
        String lower = nodeId.toLowerCase();
        return "cancelled".equals(lower) || "accepted".equals(lower) || "rejected".equals(lower);
    }

    /**
     * 修复：保存工作流前做基本结构校验，必须包含开始和结束节点，
     * 且边引用的节点 ID 必须存在。避免保存无效流程。
     */
    private void validateConfigStructure(WorkflowConfigDTO configDTO) {
        if (configDTO == null) {
            throw new BusinessException("工作流配置不能为空");
        }
        List<WorkflowNodeDTO> nodes = configDTO.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            throw new BusinessException("工作流必须至少包含一个节点");
        }

        boolean hasStart = false;
        boolean hasEnd = false;
        Set<String> nodeIds = new HashSet<>();
        for (WorkflowNodeDTO node : nodes) {
            if (node == null) {
                continue;
            }
            if (StringUtils.hasText(node.getNodeId())) {
                nodeIds.add(node.getNodeId());
            }
            if ("start".equalsIgnoreCase(node.getNodeType())) {
                hasStart = true;
            }
            if ("end".equalsIgnoreCase(node.getNodeType())) {
                hasEnd = true;
            }
        }
        if (!hasStart) {
            throw new BusinessException("工作流必须包含开始节点");
        }
        if (!hasEnd) {
            throw new BusinessException("工作流必须包含结束节点");
        }

        if (configDTO.getEdges() != null) {
            for (WorkflowEdgeDTO edge : configDTO.getEdges()) {
                if (edge == null) {
                    continue;
                }
                if (StringUtils.hasText(edge.getSourceNodeId())
                        && !isTerminalStatus(edge.getSourceNodeId())
                        && !nodeIds.contains(edge.getSourceNodeId())) {
                    throw new BusinessException("连线引用了不存在的源节点: " + edge.getSourceNodeId());
                }
                if (StringUtils.hasText(edge.getTargetNodeId())
                        && !isTerminalStatus(edge.getTargetNodeId())
                        && !nodeIds.contains(edge.getTargetNodeId())) {
                    throw new BusinessException("连线引用了不存在的目标节点: " + edge.getTargetNodeId());
                }
            }
        }
    }

    /**
     * 公共版本元数据校验（新建和更新草稿均可复用）
     *
     * @param projectId        项目ID
     * @param excludeVersionId 排除的版本ID（新建时传 null，更新时传当前版本ID）
     * @param version          目标版本号（可传 null，null 时不校验格式和重复）
     * @param versionName      目标版本名称（可传 null，null 时不校验名称）
     */
    private void validateVersionMeta(Long projectId, Long excludeVersionId, String version, String versionName) {
        // 校验版本名称
        if (StringUtils.hasText(versionName)) {
            String normalized = normalizeVersionName(versionName);
            if (normalized == null || normalized.isEmpty()) {
                throw new BusinessException("版本名称不能为空");
            }
        } else {
            throw new BusinessException("版本名称不能为空");
        }

        // 校验版本号格式和重复
        if (StringUtils.hasText(version)) {
            String normalized = normalizeVersion(version);
            if (!WorkflowVersionUtils.isValid(normalized)) {
                throw new BusinessException("版本号格式需为正整数或 1.0.0");
            }
            String normalizedName = normalizeVersionName(versionName);
            boolean duplicate = workflowVersionMapper.selectList(
                            new LambdaQueryWrapper<WorkflowVersion>()
                                    .eq(WorkflowVersion::getProjectId, projectId))
                    .stream()
                    .anyMatch(item -> {
                        if (excludeVersionId != null && item.getId().equals(excludeVersionId)) {
                            return false;
                        }
                        String itemName = normalizeVersionName(item.getName());
                        return Objects.equals(itemName, normalizedName)
                                && WorkflowVersionUtils.sameVersion(item.getVersion(), normalized);
                    });
            if (duplicate) {
                throw new BusinessException("工作流“" + normalizedName + "”下版本号 V" + normalized + " 已存在，请重新输入");
            }
        } else {
            throw new BusinessException("版本号不能为空");
        }
    }

    private WorkflowVersion findLatestInactiveVersion(Long projectId) {
        LambdaQueryWrapper<WorkflowVersion> versionQuery = new LambdaQueryWrapper<>();
        versionQuery.eq(WorkflowVersion::getProjectId, projectId)
                .eq(WorkflowVersion::getIsActive, 0);
        return workflowVersionMapper.selectList(versionQuery).stream()
                .sorted(WorkflowVersionUtils.byVersionDesc())
                .findFirst()
                .orElse(null);
    }

    /**
     * 解析本次校验/提交所针对的工作流版本。
     * <p>
     * 同一 projectId 下可能存在多个工作流。编辑器传入 versionId 时必须精确使用该版本，
     * 不能按 projectId 选择“最新草稿”，否则会把其他工作流的节点错误地作为当前工作流校验结果。
     */
    private WorkflowVersion resolveSubmissionVersion(Long projectId, Long versionId, String missingMessage) {
        if (versionId == null) {
            WorkflowVersion latestVersion = findLatestInactiveVersion(projectId);
            if (latestVersion == null) {
                throw new BusinessException(missingMessage);
            }
            return latestVersion;
        }

        WorkflowVersion version = workflowVersionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException("指定的工作流版本不存在");
        }
        if (!Objects.equals(normalizeProjectId(version.getProjectId()), projectId)) {
            throw new BusinessException("指定的工作流版本不属于当前工作流范围");
        }
        return version;
    }

    private boolean hasPendingApproval(Long versionId) {
        LambdaQueryWrapper<WorkflowApproval> approvalQuery = new LambdaQueryWrapper<>();
        approvalQuery.eq(WorkflowApproval::getWorkflowVersionId, versionId)
                .eq(WorkflowApproval::getStatus, "pending");
        return workflowApprovalMapper.selectCount(approvalQuery) > 0;
    }

    private WorkflowApproval getLatestApproval(Long versionId) {
        return workflowApprovalMapper.selectOne(new LambdaQueryWrapper<WorkflowApproval>()
                .eq(WorkflowApproval::getWorkflowVersionId, versionId)
                .orderByDesc(WorkflowApproval::getSubmittedAt)
                .last("LIMIT 1"));
    }

    private WorkflowVersion createDraftVersion(Long projectId, Long currentUserId, String version, String versionName) {
        String draftVersion = version != null ? version : WorkflowVersionUtils.suggestNext(
                workflowVersionMapper.selectList(new LambdaQueryWrapper<WorkflowVersion>()
                                .eq(WorkflowVersion::getProjectId, projectId))
                        .stream()
                        .sorted(WorkflowVersionUtils.byVersionDesc())
                        .map(WorkflowVersion::getVersion)
                        .findFirst()
                        .orElse(null));
        String draftName = versionName != null ? versionName : "草稿版本 v" + draftVersion;
        WorkflowVersion newVersion = new WorkflowVersion();
        newVersion.setProjectId(projectId);
        // 关联到工作流定义（缺失则按 (projectId, name) 建立，与历史回填逻辑一致）
        newVersion.setWorkflowDefinitionId(resolveOrCreateDefinition(projectId, draftName, currentUserId));
        newVersion.setVersion(draftVersion);
        newVersion.setName(draftName);
        newVersion.setDefinition("{\"nodes\":[],\"edges\":[]}");
        newVersion.setIsActive(0);
        newVersion.setActivationStatus("draft");
        newVersion.setCreatorId(currentUserId);
        newVersion.setCreatedAt(LocalDateTime.now());
        newVersion.setUpdatedAt(LocalDateTime.now());
        workflowVersionMapper.insert(newVersion);
        return newVersion;
    }

    /**
     * 按 (projectId, name) 解析或创建工作流定义，返回定义ID。
     * <p>
     * 用于新建版本时关联工作流定义。与 {@code WorkflowDefinitionServiceImpl#backfill} 的分组策略一致，
     * 历史上同一 (projectId, name) 的版本被视为同一工作流的多个版本。
     */
    private Long resolveOrCreateDefinition(Long projectId, String name, Long currentUserId) {
        String definitionName = StringUtils.hasText(name) ? name.trim() : "未命名工作流";
        WorkflowDefinition existing = workflowDefinitionMapper.selectOne(
                new LambdaQueryWrapper<WorkflowDefinition>()
                        .eq(WorkflowDefinition::getProjectId, projectId)
                        .eq(WorkflowDefinition::getName, definitionName)
                        .last("LIMIT 1"));
        if (existing != null) {
            return existing.getId();
        }
        WorkflowDefinition def = new WorkflowDefinition();
        def.setName(definitionName);
        def.setProjectId(projectId);
        def.setCreatorId(currentUserId);
        workflowDefinitionMapper.insert(def);
        return def.getId();
    }

    private String normalizeVersion(String version) {
        return WorkflowVersionUtils.normalize(version);
    }

    private String normalizeComment(String comment) {
        if (comment == null) {
            return null;
        }
        String trimmed = comment.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeVersionName(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        return name.trim();
    }

    private Long normalizeProjectId(Long projectId) {
        if (projectId == null || projectId <= 0) {
            return WorkflowVersionResolver.GLOBAL_PROJECT_ID;
        }
        return projectId;
    }

    private WorkflowVersionDTO toVersionDTO(WorkflowVersion version) {
        WorkflowVersionDTO dto = new WorkflowVersionDTO();
        BeanUtils.copyProperties(version, dto);
        dto.setVersion(normalizeVersion(version.getVersion()));

        // 填充工作流定义名称（workflowDefinitionId 已由 copyProperties 拷贝）
        if (version.getWorkflowDefinitionId() != null) {
            WorkflowDefinition def = workflowDefinitionMapper.selectById(version.getWorkflowDefinitionId());
            if (def != null) {
                dto.setWorkflowDefinitionName(def.getName());
            }
        }

        if (version.getCreatorId() != null) {
            SysUser user = sysUserMapper.selectById(version.getCreatorId());
            if (user != null) {
                dto.setCreatorName(StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername());
            }
        }

        // 填充知识库名称
        if (version.getKnowledgeBaseId() != null) {
            KnowledgeBase kb = knowledgeBaseMapper.selectById(version.getKnowledgeBaseId());
            if (kb != null) {
                dto.setKnowledgeBaseName(kb.getName());
            }
        }

        LambdaQueryWrapper<WorkflowApproval> approvalQuery = new LambdaQueryWrapper<>();
        approvalQuery.eq(WorkflowApproval::getWorkflowVersionId, version.getId())
                .orderByDesc(WorkflowApproval::getSubmittedAt)
                .last("LIMIT 1");
        WorkflowApproval latestApproval = workflowApprovalMapper.selectOne(approvalQuery);
        if (latestApproval != null) {
            dto.setLatestApprovalStatus(latestApproval.getStatus() == null ? null : latestApproval.getStatus().toUpperCase());
            dto.setLatestApprovalComment(latestApproval.getComment());
            dto.setLatestSubmittedAt(latestApproval.getSubmittedAt());
            dto.setLatestApprovedAt(latestApproval.getApprovedAt());
        }

        return dto;
    }

    private WorkflowApprovalDTO toApprovalDTO(WorkflowApproval approval) {
        WorkflowApprovalDTO dto = new WorkflowApprovalDTO();
        BeanUtils.copyProperties(approval, dto);

        if (dto.getStatus() != null) {
            dto.setStatus(dto.getStatus().toUpperCase());
        }

        WorkflowVersion version = workflowVersionMapper.selectById(approval.getWorkflowVersionId());
        if (version != null) {
            dto.setProjectId(version.getProjectId());
            dto.setVersion(normalizeVersion(version.getVersion()));
            dto.setVersionName(version.getName());

            if (WorkflowVersionResolver.GLOBAL_PROJECT_ID.equals(version.getProjectId())) {
                dto.setProjectName(GLOBAL_WORKFLOW_NAME);
            } else {
                Project project = projectMapper.selectById(version.getProjectId());
                if (project != null) {
                    dto.setProjectName(project.getName());
                }
            }
        }

        if (approval.getSubmitterId() != null) {
            SysUser user = sysUserMapper.selectById(approval.getSubmitterId());
            if (user != null) {
                dto.setSubmitterName(StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername());
            }
        }

        if (approval.getApproverId() != null) {
            SysUser approver = sysUserMapper.selectById(approval.getApproverId());
            if (approver != null) {
                dto.setApproverName(StringUtils.hasText(approver.getRealName()) ? approver.getRealName() : approver.getUsername());
            }
        }

        return dto;
    }
}
