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
import com.demand.system.module.workflow.service.WorkflowConfigService;
import com.demand.system.module.workflow.dto.WorkflowValidationIssue;
import com.demand.system.module.workflow.engine.WorkflowGraphCompiler;
import com.demand.system.module.workflow.engine.WorkflowGraphValidator;
import com.demand.system.module.workflow.service.WorkflowActivationService;
import com.demand.system.module.workflow.support.WorkflowVersionUtils;
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
import java.util.Set;
import java.util.stream.Collectors;


@Service
public class WorkflowConfigServiceImpl implements WorkflowConfigService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WorkflowConfigServiceImpl.class);
    private static final String GLOBAL_WORKFLOW_NAME = "全局流程";

    private final WorkflowVersionMapper workflowVersionMapper;
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

    public WorkflowConfigServiceImpl(WorkflowVersionMapper workflowVersionMapper, WorkflowNodeMapper workflowNodeMapper,
                                   WorkflowEdgeMapper workflowEdgeMapper, WorkflowApprovalMapper workflowApprovalMapper,
                                   WorkflowInstanceMapper workflowInstanceMapper, WorkflowNodePermissionMapper workflowNodePermissionMapper,
                                   ProjectMapper projectMapper, SysUserMapper sysUserMapper,
                                   WorkflowGraphValidator workflowGraphValidator, WorkflowGraphCompiler workflowGraphCompiler,
                                   WorkflowActivationService workflowActivationService) {
        this.workflowVersionMapper = workflowVersionMapper;
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

        // 修复：保存前做连通性和基本结构校验，避免保存无效流程
        validateConfigStructure(configDTO);

        Long normalizedProjectId = normalizeProjectId(projectId);
        WorkflowVersion existingDraft = findLatestInactiveVersion(normalizedProjectId);

        // 修复：现有草稿已提交审核时禁止保存
        if (existingDraft != null && hasPendingApproval(existingDraft.getId())) {
            throw new BusinessException("现有草稿已提交审核，请等待审核完成后再保存");
        }

        // 修复：总是创建新草稿版本，不再覆盖现有未审核的 draft
        // 仅将 activation_status=draft 的旧版本归档，保留 inactive 历史版本
        if (existingDraft != null && "draft".equals(existingDraft.getActivationStatus())) {
            existingDraft.setActivationStatus("archived");
            workflowVersionMapper.updateById(existingDraft);
        }
        WorkflowVersion draftVersion = createDraftVersion(normalizedProjectId, currentUserId);

        // 保存新的节点
        if (configDTO.getNodes() != null && !configDTO.getNodes().isEmpty()) {
            for (WorkflowNodeDTO nodeDTO : configDTO.getNodes()) {
                WorkflowNode node = new WorkflowNode();
                BeanUtils.copyProperties(nodeDTO, node);
                node.setWorkflowVersionId(draftVersion.getId());
                // 修复：给 node_id 加版本前缀，避免跨版本冲突
                if (StringUtils.hasText(node.getNodeId()) && !node.getNodeId().startsWith("v" + draftVersion.getId() + "_")) {
                    node.setNodeId("v" + draftVersion.getId() + "_" + node.getNodeId());
                }
                workflowNodeMapper.insert(node);
            }
        }

        // 保存新的连线
        if (configDTO.getEdges() != null && !configDTO.getEdges().isEmpty()) {
            for (WorkflowEdgeDTO edgeDTO : configDTO.getEdges()) {
                WorkflowEdge edge = new WorkflowEdge();
                BeanUtils.copyProperties(edgeDTO, edge);
                edge.setWorkflowVersionId(draftVersion.getId());
                // 同步给 edge 引用添加版本前缀
                String prefix = "v" + draftVersion.getId() + "_";
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

        List<WorkflowNode> savedNodes = workflowNodeMapper.selectList(new LambdaQueryWrapper<WorkflowNode>()
                .eq(WorkflowNode::getWorkflowVersionId, draftVersion.getId()));
        List<WorkflowEdge> savedEdges = workflowEdgeMapper.selectList(new LambdaQueryWrapper<WorkflowEdge>()
                .eq(WorkflowEdge::getWorkflowVersionId, draftVersion.getId()));
        workflowGraphValidator.validateOrThrow(savedNodes, savedEdges);
        WorkflowGraphCompiler.CompiledWorkflow compiled = workflowGraphCompiler.compile(draftVersion.getId(), savedNodes, savedEdges);
        draftVersion.setDefinition(compiled.definitionJson());
        draftVersion.setRuntimeHash(compiled.runtimeHash());
        draftVersion.setActivationStatus("draft");
        workflowVersionMapper.updateById(draftVersion);

        log.info("保存工作流配置成功，projectId={}, versionId={}", normalizedProjectId, draftVersion.getId());
        return toVersionDTO(draftVersion);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitForApproval(Long projectId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException("用户未登录");
        }

        Long normalizedProjectId = normalizeProjectId(projectId);
        WorkflowVersion draftVersion = findLatestInactiveVersion(normalizedProjectId);

        if (draftVersion == null) {
            throw new BusinessException("没有可提交的草稿版本");
        }

        if (hasPendingApproval(draftVersion.getId())) {
            throw new BusinessException("该版本已提交审核，请勿重复提交");
        }

        List<WorkflowNode> nodes = workflowNodeMapper.selectList(new LambdaQueryWrapper<WorkflowNode>()
                .eq(WorkflowNode::getWorkflowVersionId, draftVersion.getId()));
        List<WorkflowEdge> edges = workflowEdgeMapper.selectList(new LambdaQueryWrapper<WorkflowEdge>()
                .eq(WorkflowEdge::getWorkflowVersionId, draftVersion.getId()));
        workflowGraphValidator.validateOrThrow(nodes, edges);

        draftVersion.setActivationStatus("pending");
        workflowVersionMapper.updateById(draftVersion);

        // 创建审核记录
        WorkflowApproval approval = new WorkflowApproval();
        approval.setWorkflowVersionId(draftVersion.getId());
        approval.setSubmitterId(currentUserId);
        approval.setStatus("pending");
        approval.setSubmittedAt(LocalDateTime.now());
        workflowApprovalMapper.insert(approval);

        log.info("提交工作流审核成功，projectId={}, versionId={}", normalizedProjectId, draftVersion.getId());
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

        String normalizedName = normalizeVersionName(updateDTO.getName());
        if (normalizedName == null) {
            throw new BusinessException("版本名称不能为空");
        }

        String targetVersion = normalizeVersion(updateDTO.getVersion());
        if (!WorkflowVersionUtils.isValid(targetVersion)) {
            throw new BusinessException("版本号格式需为正整数或 1.0.0");
        }

        boolean duplicateVersion = workflowVersionMapper.selectList(new LambdaQueryWrapper<WorkflowVersion>()
                        .eq(WorkflowVersion::getProjectId, version.getProjectId()))
                .stream()
                .anyMatch(item -> !item.getId().equals(versionId) && WorkflowVersionUtils.sameVersion(item.getVersion(), targetVersion));
        if (duplicateVersion) {
            throw new BusinessException("版本号 V" + targetVersion + " 已存在，请重新输入");
        }

        version.setVersion(targetVersion);
        version.setName(normalizedName);
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
    public List<WorkflowValidationIssue> validateVersion(Long versionId) {
        return workflowActivationService.validateVersion(versionId);
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
            workflowVersionMapper.updateById(version);
        }

        log.info("审核拒绝工作流版本，approvalId={}, versionId={}", approvalId, approval.getWorkflowVersionId());
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

    private WorkflowVersion findLatestInactiveVersion(Long projectId) {
        LambdaQueryWrapper<WorkflowVersion> versionQuery = new LambdaQueryWrapper<>();
        versionQuery.eq(WorkflowVersion::getProjectId, projectId)
                .eq(WorkflowVersion::getIsActive, 0);
        return workflowVersionMapper.selectList(versionQuery).stream()
                .sorted(WorkflowVersionUtils.byVersionDesc())
                .findFirst()
                .orElse(null);
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

    private WorkflowVersion createDraftVersion(Long projectId, Long currentUserId) {
        String latestVersion = workflowVersionMapper.selectList(new LambdaQueryWrapper<WorkflowVersion>()
                        .eq(WorkflowVersion::getProjectId, projectId))
                .stream()
                .sorted(WorkflowVersionUtils.byVersionDesc())
                .map(WorkflowVersion::getVersion)
                .findFirst()
                .orElse(null);
        String nextVersion = WorkflowVersionUtils.suggestNext(latestVersion);
        WorkflowVersion draftVersion = new WorkflowVersion();
        draftVersion.setProjectId(projectId);
        draftVersion.setVersion(nextVersion);
        draftVersion.setName("草稿版本 v" + nextVersion);
        draftVersion.setDefinition("{\"nodes\":[],\"edges\":[]}");
        draftVersion.setIsActive(0);
        draftVersion.setActivationStatus("draft");
        draftVersion.setCreatorId(currentUserId);
        draftVersion.setCreatedAt(LocalDateTime.now());
        workflowVersionMapper.insert(draftVersion);
        return draftVersion;
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

        if (version.getCreatorId() != null) {
            SysUser user = sysUserMapper.selectById(version.getCreatorId());
            if (user != null) {
                dto.setCreatorName(StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername());
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
            dto.setVersion(version.getVersion());
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
