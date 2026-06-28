package com.demand.system.module.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.workflow.dto.WorkflowEdgeDTO;
import com.demand.system.module.workflow.dto.WorkflowExportDTO;
import com.demand.system.module.workflow.dto.WorkflowImportResponseDTO;
import com.demand.system.module.workflow.dto.WorkflowNodeDTO;
import com.demand.system.module.workflow.engine.WorkflowGraphCompiler;
import com.demand.system.module.workflow.engine.WorkflowGraphValidator;
import com.demand.system.module.workflow.engine.WorkflowVersionResolver;
import com.demand.system.module.workflow.entity.WorkflowEdge;
import com.demand.system.module.workflow.entity.WorkflowNode;
import com.demand.system.module.workflow.entity.WorkflowVersion;
import com.demand.system.module.workflow.mapper.WorkflowEdgeMapper;
import com.demand.system.module.workflow.mapper.WorkflowNodeMapper;
import com.demand.system.module.workflow.mapper.WorkflowVersionMapper;
import com.demand.system.module.workflow.service.WorkflowImportService;
import com.demand.system.module.workflow.support.WorkflowVersionUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class WorkflowImportServiceImpl implements WorkflowImportService {

    private final WorkflowVersionMapper workflowVersionMapper;
    private final WorkflowNodeMapper workflowNodeMapper;
    private final WorkflowEdgeMapper workflowEdgeMapper;
    private final WorkflowGraphValidator workflowGraphValidator;
    private final WorkflowGraphCompiler workflowGraphCompiler;

    public WorkflowImportServiceImpl(WorkflowVersionMapper workflowVersionMapper,
                                     WorkflowNodeMapper workflowNodeMapper,
                                     WorkflowEdgeMapper workflowEdgeMapper,
                                     WorkflowGraphValidator workflowGraphValidator,
                                     WorkflowGraphCompiler workflowGraphCompiler) {
        this.workflowVersionMapper = workflowVersionMapper;
        this.workflowNodeMapper = workflowNodeMapper;
        this.workflowEdgeMapper = workflowEdgeMapper;
        this.workflowGraphValidator = workflowGraphValidator;
        this.workflowGraphCompiler = workflowGraphCompiler;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowImportResponseDTO importWorkflow(WorkflowExportDTO data, Long projectId) {
        validateImportData(data);

        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException("用户未登录");
        }

        Long normalizedProjectId = normalizeProjectId(projectId);
        String originalName = normalizeName(data.getWorkflow().getName());
        String resolvedName = resolveWorkflowName(originalName, normalizedProjectId);
        String resolvedVersion = suggestNextVersion(normalizedProjectId);

        WorkflowVersion newVersion = new WorkflowVersion();
        newVersion.setProjectId(normalizedProjectId);
        newVersion.setVersion(resolvedVersion);
        newVersion.setName(resolvedName);
        newVersion.setDefinition("{\"nodes\":[],\"edges\":[]}");
        newVersion.setIsActive(0);
        newVersion.setIsTemplate(0);
        newVersion.setCopyCount(0);
        newVersion.setActivationStatus("draft");
        newVersion.setCreatorId(currentUserId);
        newVersion.setCreatedAt(LocalDateTime.now());
        newVersion.setChangeLog("由导入文件创建，来源版本：V" + data.getWorkflow().getVersion());
        workflowVersionMapper.insert(newVersion);

        Map<String, String> nodeIdMapping = saveImportedNodes(data.getWorkflow().getConfig().getNodes(), newVersion.getId());
        saveImportedEdges(data.getWorkflow().getConfig().getEdges(), newVersion.getId(), nodeIdMapping);

        List<WorkflowNode> savedNodes = workflowNodeMapper.selectList(new LambdaQueryWrapper<WorkflowNode>()
                .eq(WorkflowNode::getWorkflowVersionId, newVersion.getId()));
        List<WorkflowEdge> savedEdges = workflowEdgeMapper.selectList(new LambdaQueryWrapper<WorkflowEdge>()
                .eq(WorkflowEdge::getWorkflowVersionId, newVersion.getId()));
        workflowGraphValidator.validateOrThrow(savedNodes, savedEdges);
        WorkflowGraphCompiler.CompiledWorkflow compiled = workflowGraphCompiler.compile(newVersion.getId(), savedNodes, savedEdges);
        newVersion.setDefinition(compiled.definitionJson());
        newVersion.setRuntimeHash(compiled.runtimeHash());
        newVersion.setConfigHash(compiled.configHash());
        workflowVersionMapper.updateById(newVersion);

        WorkflowImportResponseDTO.ConflictInfo conflictInfo = new WorkflowImportResponseDTO.ConflictInfo();
        conflictInfo.setNameConflict(!resolvedName.equals(originalName));
        conflictInfo.setVersionConflict(true);
        conflictInfo.setResolvedName(resolvedName);
        conflictInfo.setResolvedVersion(resolvedVersion);

        WorkflowImportResponseDTO response = new WorkflowImportResponseDTO();
        response.setSuccess(true);
        response.setVersionId(newVersion.getId());
        response.setVersion(resolvedVersion);
        response.setName(resolvedName);
        response.setMessage("导入成功");
        response.setConflicts(conflictInfo);
        return response;
    }

    private void validateImportData(WorkflowExportDTO data) {
        if (data == null || data.getWorkflow() == null) {
            throw new BusinessException("导入数据不能为空");
        }
        WorkflowExportDTO.WorkflowData workflow = data.getWorkflow();
        if (!StringUtils.hasText(workflow.getName())) {
            throw new BusinessException("工作流名称不能为空");
        }
        if (workflow.getConfig() == null) {
            throw new BusinessException("工作流配置不能为空");
        }
        List<WorkflowNodeDTO> nodes = workflow.getConfig().getNodes();
        if (nodes == null || nodes.isEmpty()) {
            throw new BusinessException("工作流至少需要一个节点");
        }
        if (workflow.getConfig().getEdges() == null) {
            throw new BusinessException("工作流边配置不能为空");
        }
        validateRawReferences(workflow.getConfig());
    }

    private void validateRawReferences(WorkflowExportDTO.WorkflowConfigData config) {
        Set<String> nodeIds = new HashSet<>();
        for (WorkflowNodeDTO node : config.getNodes()) {
            if (node == null || !StringUtils.hasText(node.getNodeId())) {
                throw new BusinessException("导入文件存在未配置 nodeId 的节点");
            }
            if (!nodeIds.add(node.getNodeId())) {
                throw new BusinessException("导入文件存在重复节点ID: " + node.getNodeId());
            }
        }
        for (WorkflowEdgeDTO edge : config.getEdges()) {
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

    private Map<String, String> saveImportedNodes(List<WorkflowNodeDTO> nodes, Long versionId) {
        Map<String, String> nodeIdMapping = new HashMap<>();
        String prefix = "v" + versionId + "_";
        for (WorkflowNodeDTO nodeDTO : nodes) {
            String oldNodeId = nodeDTO.getNodeId();
            String newNodeId = prefix + UUID.randomUUID().toString().replace("-", "");
            nodeIdMapping.put(oldNodeId, newNodeId);

            WorkflowNode node = new WorkflowNode();
            BeanUtils.copyProperties(nodeDTO, node);
            node.setId(null);
            node.setWorkflowVersionId(versionId);
            node.setNodeId(newNodeId);
            normalizeNodeAssigneeFields(node);
            workflowNodeMapper.insert(node);
        }
        return nodeIdMapping;
    }

    private void saveImportedEdges(List<WorkflowEdgeDTO> edges, Long versionId, Map<String, String> nodeIdMapping) {
        if (edges == null || edges.isEmpty()) {
            return;
        }
        String prefix = "v" + versionId + "_";
        for (WorkflowEdgeDTO edgeDTO : edges) {
            WorkflowEdge edge = new WorkflowEdge();
            BeanUtils.copyProperties(edgeDTO, edge);
            edge.setId(null);
            edge.setWorkflowVersionId(versionId);
            edge.setEdgeId(prefix + UUID.randomUUID().toString().replace("-", ""));
            edge.setSourceNodeId(resolveImportedNodeReference(edgeDTO.getSourceNodeId(), nodeIdMapping));
            edge.setTargetNodeId(resolveImportedNodeReference(edgeDTO.getTargetNodeId(), nodeIdMapping));
            workflowEdgeMapper.insert(edge);
        }
    }

    private String resolveImportedNodeReference(String nodeId, Map<String, String> nodeIdMapping) {
        if (!StringUtils.hasText(nodeId) || isTerminalStatus(nodeId)) {
            return nodeId;
        }
        String mapped = nodeIdMapping.get(nodeId);
        if (!StringUtils.hasText(mapped)) {
            throw new BusinessException("工作流配置引用完整性校验失败: " + nodeId);
        }
        return mapped;
    }

    private String resolveWorkflowName(String originalName, Long projectId) {
        String candidate = originalName;
        int copyIndex = 1;
        while (existsName(projectId, candidate)) {
            candidate = originalName + "(副本" + copyIndex + ")";
            copyIndex++;
            if (copyIndex > 999) {
                throw new BusinessException("无法生成唯一的工作流名称");
            }
        }
        return candidate;
    }

    private boolean existsName(Long projectId, String name) {
        return workflowVersionMapper.selectCount(new LambdaQueryWrapper<WorkflowVersion>()
                .eq(WorkflowVersion::getProjectId, projectId)
                .eq(WorkflowVersion::getName, name)) > 0;
    }

    private String suggestNextVersion(Long projectId) {
        return WorkflowVersionUtils.suggestNext(workflowVersionMapper.selectList(new LambdaQueryWrapper<WorkflowVersion>()
                        .eq(WorkflowVersion::getProjectId, projectId))
                .stream()
                .sorted(WorkflowVersionUtils.byVersionDesc())
                .map(WorkflowVersion::getVersion)
                .findFirst()
                .orElse(null));
    }

    private String normalizeName(String name) {
        String normalized = name == null ? null : name.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException("工作流名称不能为空");
        }
        return normalized;
    }

    private Long normalizeProjectId(Long projectId) {
        if (projectId == null || projectId <= 0) {
            return WorkflowVersionResolver.GLOBAL_PROJECT_ID;
        }
        return projectId;
    }

    private boolean isTerminalStatus(String nodeId) {
        if (nodeId == null) {
            return false;
        }
        String lower = nodeId.toLowerCase();
        return "cancelled".equals(lower) || "accepted".equals(lower) || "rejected".equals(lower);
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
        return longValue == null ? null : longValue.intValue();
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
        return rawList.stream()
                .map(this::toLong)
                .filter(item -> item != null)
                .toList();
    }
}
