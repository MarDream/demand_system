package com.demand.system.module.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.workflow.dto.*;
import com.demand.system.module.workflow.entity.*;
import com.demand.system.module.workflow.mapper.*;
import com.demand.system.module.workflow.service.WorkflowCopyService;
import com.demand.system.module.user.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 工作流复制服务实现
 */
@Service
public class WorkflowCopyServiceImpl implements WorkflowCopyService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowCopyServiceImpl.class);

    private static final int SYNC_COPY_THRESHOLD = 50; // 节点数小于50，同步复制
    private static final int BATCH_SIZE = 50; // 批量插入大小

    // 敏感字段正则模式
    private static final List<String> SENSITIVE_FIELD_PATTERNS = Arrays.asList(
        ".*password.*", ".*secret.*", ".*token.*", ".*key.*",
        ".*phone.*", ".*email.*", ".*credential.*", ".*apikey.*"
    );

    private final WorkflowVersionMapper workflowVersionMapper;
    private final WorkflowNodeMapper workflowNodeMapper;
    private final WorkflowEdgeMapper workflowEdgeMapper;
    private final WorkflowAuditLogMapper auditLogMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    public WorkflowCopyServiceImpl(WorkflowVersionMapper workflowVersionMapper,
                                   WorkflowNodeMapper workflowNodeMapper,
                                   WorkflowEdgeMapper workflowEdgeMapper,
                                   WorkflowAuditLogMapper auditLogMapper,
                                   UserMapper userMapper,
                                   ObjectMapper objectMapper) {
        this.workflowVersionMapper = workflowVersionMapper;
        this.workflowNodeMapper = workflowNodeMapper;
        this.workflowEdgeMapper = workflowEdgeMapper;
        this.auditLogMapper = auditLogMapper;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowCopyResponse copyWorkflow(Long sourceVersionId, WorkflowCopyRequest request,
                                            Long operatorId, String operatorName,
                                            String ipAddress, String userAgent) {
        try {
            // 1. 查询源工作流
            WorkflowVersion sourceVersion = workflowVersionMapper.selectById(sourceVersionId);
            if (sourceVersion == null) {
                throw new BusinessException("源工作流不存在");
            }

            // 2. 确定目标项目ID
            Long targetProjectId = request.getTargetProjectId() != null 
                ? request.getTargetProjectId() 
                : sourceVersion.getProjectId();

            // 3. 生成唯一名称
            String finalName = request.getNewName();
            if (checkNameConflict(finalName, targetProjectId, operatorId)) {
                finalName = generateUniqueName(finalName, targetProjectId, operatorId);
            }

            // 4. 生成新版本号
            String newVersionStr = request.getNewVersion();
            if (newVersionStr == null || newVersionStr.isEmpty()) {
                newVersionStr = generateNextVersion(targetProjectId);
            }

            // 5. 创建新工作流版本
            WorkflowVersion newWorkflowVersion = createNewVersion(sourceVersion, request, finalName,
                                                         newVersionStr, targetProjectId, operatorId);
            workflowVersionMapper.insert(newWorkflowVersion);

            // 6. 复制节点
            Map<String, String> nodeIdMap = new HashMap<>();
            int copiedNodeCount = 0;
            if (request.getIncludeNodes()) {
                copiedNodeCount = copyNodes(sourceVersionId, newWorkflowVersion.getId(), request, nodeIdMap);
            }

            // 7. 复制连线
            int copiedEdgeCount = 0;
            if (request.getIncludeEdges() && !nodeIdMap.isEmpty()) {
                copiedEdgeCount = copyEdges(sourceVersionId, newWorkflowVersion.getId(), nodeIdMap);
            }

            // 8. 更新源工作流的复制计数
            WorkflowVersion updateVersion = new WorkflowVersion();
            updateVersion.setId(sourceVersionId);
            Integer currentCopyCount = sourceVersion.getCopyCount() != null ? sourceVersion.getCopyCount() : 0;
            updateVersion.setCopyCount(currentCopyCount + 1);
            workflowVersionMapper.updateById(updateVersion);

            // 9. 记录审计日志
            createAuditLog(newWorkflowVersion.getId(), "copied", operatorId, operatorName,
                Map.of(
                    "source_version_id", sourceVersionId,
                    "source_version_name", sourceVersion.getName(),
                    "new_version_name", finalName,
                    "new_version", newVersionStr,
                    "copied_node_count", copiedNodeCount,
                    "copied_edge_count", copiedEdgeCount,
                    "options", request
                ),
                ipAddress, userAgent
            );

            // 10. 构造响应
            WorkflowCopyResponse response = new WorkflowCopyResponse(
                newWorkflowVersion.getId(), finalName, newVersionStr
            );
            response.setCopiedNodeCount(copiedNodeCount);
            response.setCopiedEdgeCount(copiedEdgeCount);
            response.setMode("sync");

            log.info("工作流复制成功: source={}, target={}, nodes={}, edges={}",
                sourceVersionId, newWorkflowVersion.getId(), copiedNodeCount, copiedEdgeCount);

            return response;

        } catch (Exception e) {
            log.error("工作流复制失败: sourceVersionId={}, error={}", sourceVersionId, e.getMessage(), e);
            throw new BusinessException("工作流复制失败: " + e.getMessage());
        }
    }

    /**
     * 创建新工作流版本
     */
    private WorkflowVersion createNewVersion(WorkflowVersion source, WorkflowCopyRequest request,
                                            String newName, String newVersion, 
                                            Long targetProjectId, Long operatorId) {
        WorkflowVersion newVer = new WorkflowVersion();
        newVer.setProjectId(targetProjectId);
        newVer.setVersion(newVersion);
        newVer.setName(newName);
        newVer.setSourceVersionId(source.getId().intValue());
        newVer.setIsTemplate(0);
        newVer.setCopyCount(0);
        newVer.setIsActive(0);
        newVer.setActivationStatus("draft");
        newVer.setCreatorId(operatorId);
        newVer.setCreatedAt(LocalDateTime.now());
        
        // 复制描述
        if (request.getIncludeDescription() && source.getDefinition() != null) {
            newVer.setDefinition(source.getDefinition());
        }
        
        return newVer;
    }

    /**
     * 复制节点
     */
    private int copyNodes(Long sourceVersionId, Long targetVersionId, 
                         WorkflowCopyRequest request, Map<String, String> nodeIdMap) {
        // 查询源节点
        List<WorkflowNode> sourceNodes = workflowNodeMapper.selectList(
            new LambdaQueryWrapper<WorkflowNode>()
                .eq(WorkflowNode::getWorkflowVersionId, sourceVersionId)
        );

        if (sourceNodes.isEmpty()) {
            return 0;
        }

        // 批量复制节点
        for (WorkflowNode sourceNode : sourceNodes) {
            WorkflowNode newNode = new WorkflowNode();
            newNode.setWorkflowVersionId(targetVersionId);
            // 为复制后的节点生成新 ID，避免唯一约束冲突
            // 格式：v{targetVersionId}_{原nodeId}，与 WorkflowConfigServiceImpl 保持一致
            String newNodeId = "v" + targetVersionId + "_" + sourceNode.getNodeId();
            newNode.setNodeId(newNodeId);
            nodeIdMap.put(sourceNode.getNodeId(), newNodeId);
            newNode.setNodeType(sourceNode.getNodeType());
            newNode.setNodeName(sourceNode.getNodeName());
            newNode.setPositionX(sourceNode.getPositionX());
            newNode.setPositionY(sourceNode.getPositionY());
            newNode.setTimeoutHours(sourceNode.getTimeoutHours());
            newNode.setTimeoutAction(sourceNode.getTimeoutAction());

            // 处理审批人配置
            if (request.getResetApprovers()) {
                newNode.setAssigneeType(null);
                newNode.setAssigneeRoleId(null);
                newNode.setAssigneeRoleGroupId(null);
                newNode.setAssigneeOrgId(null);
                newNode.setAssigneeUserIds(null);
            } else {
                newNode.setAssigneeType(sourceNode.getAssigneeType());
                newNode.setAssigneeRoleId(sourceNode.getAssigneeRoleId());
                newNode.setAssigneeRoleGroupId(sourceNode.getAssigneeRoleGroupId());
                newNode.setAssigneeOrgId(sourceNode.getAssigneeOrgId());
                newNode.setAssigneeUserIds(sourceNode.getAssigneeUserIds());
            }

            // 处理节点属性（脱敏）
            if (sourceNode.getProperties() != null) {
                Map<String, Object> sanitizedProps = sanitizeProperties(
                    sourceNode.getProperties(), request
                );
                newNode.setProperties(sanitizedProps);
            }

            workflowNodeMapper.insert(newNode);
            nodeIdMap.put(sourceNode.getNodeId(), newNode.getNodeId());
        }

        return sourceNodes.size();
    }

    /**
     * 复制连线
     */
    private int copyEdges(Long sourceVersionId, Long targetVersionId, Map<String, String> nodeIdMap) {
        // 查询源连线
        List<WorkflowEdge> sourceEdges = workflowEdgeMapper.selectList(
            new LambdaQueryWrapper<WorkflowEdge>()
                .eq(WorkflowEdge::getWorkflowVersionId, sourceVersionId)
        );

        if (sourceEdges.isEmpty()) {
            return 0;
        }

        // 批量复制连线
        for (WorkflowEdge sourceEdge : sourceEdges) {
            // 验证节点ID是否存在
            if (!nodeIdMap.containsKey(sourceEdge.getSourceNodeId()) ||
                !nodeIdMap.containsKey(sourceEdge.getTargetNodeId())) {
                log.warn("跳过无效连线: edgeId={}, sourceNode={}, targetNode={}",
                    sourceEdge.getEdgeId(), sourceEdge.getSourceNodeId(), sourceEdge.getTargetNodeId());
                continue;
            }

            WorkflowEdge newEdge = new WorkflowEdge();
            newEdge.setWorkflowVersionId(targetVersionId);
            // 为复制后的连线生成新 ID，避免唯一约束冲突
            // 格式：v{targetVersionId}_{原edgeId}，与节点 ID 前缀规则保持一致
            String newEdgeId = "v" + targetVersionId + "_" + sourceEdge.getEdgeId();
            newEdge.setEdgeId(newEdgeId);
            // 使用 nodeIdMap 映射后的新节点 ID
            newEdge.setSourceNodeId(nodeIdMap.get(sourceEdge.getSourceNodeId()));
            newEdge.setTargetNodeId(nodeIdMap.get(sourceEdge.getTargetNodeId()));
            newEdge.setLabel(sourceEdge.getLabel());
            newEdge.setCondition(sourceEdge.getCondition());
            newEdge.setProperties(sourceEdge.getProperties());

            workflowEdgeMapper.insert(newEdge);
        }

        return sourceEdges.size();
    }

    /**
     * 脱敏属性配置
     */
    private Map<String, Object> sanitizeProperties(Map<String, Object> properties, WorkflowCopyRequest request) {
        if (properties == null || properties.isEmpty()) {
            return properties;
        }

        if (!request.getResetSensitiveData()) {
            return new HashMap<>(properties);
        }

        Map<String, Object> sanitized = new HashMap<>();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // 检查是否为敏感字段
            boolean isSensitive = isSensitiveField(key, request.getCustomSensitiveFields());

            if (isSensitive && value != null) {
                sanitized.put(key, maskValue(value.toString()));
            } else if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) value;
                sanitized.put(key, sanitizeProperties(nestedMap, request));
            } else {
                sanitized.put(key, value);
            }
        }

        return sanitized;
    }

    /**
     * 判断是否为敏感字段
     */
    private boolean isSensitiveField(String fieldName, List<String> customPatterns) {
        String lowerFieldName = fieldName.toLowerCase();

        // 检查内置敏感模式
        for (String pattern : SENSITIVE_FIELD_PATTERNS) {
            if (Pattern.matches(pattern, lowerFieldName)) {
                return true;
            }
        }

        // 检查自定义敏感字段
        if (customPatterns != null) {
            for (String customField : customPatterns) {
                if (lowerFieldName.contains(customField.toLowerCase())) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 脱敏值
     */
    private String maskValue(String value) {
        if (value == null || value.length() < 4) {
            return "***";
        }
        return value.substring(0, 2) + "***" + value.substring(value.length() - 2);
    }

    @Override
    public Page<WorkflowTemplateDTO> getTemplates(Page<WorkflowTemplateDTO> page, String keyword,
                                                  Boolean includeMyWorkflows, Long currentUserId) {
        // TODO: 实现模板列表查询（需要连表查询user表）
        return page;
    }

    @Override
    public boolean checkNameConflict(String name, Long projectId, Long currentUserId) {
        Long count = workflowVersionMapper.selectCount(
            new LambdaQueryWrapper<WorkflowVersion>()
                .eq(WorkflowVersion::getProjectId, projectId)
                .eq(WorkflowVersion::getName, name)
        );
        return count > 0;
    }

    @Override
    public String generateUniqueName(String baseName, Long projectId, Long currentUserId) {
        // 检测现有名称的编号模式
        Pattern pattern = Pattern.compile("^" + Pattern.quote(baseName) + "\\s*\\((\\d+)\\)$");
        
        List<WorkflowVersion> existingVersions = workflowVersionMapper.selectList(
            new LambdaQueryWrapper<WorkflowVersion>()
                .eq(WorkflowVersion::getProjectId, projectId)
                .likeRight(WorkflowVersion::getName, baseName)
        );

        int maxNum = 0;
        for (WorkflowVersion version : existingVersions) {
            Matcher matcher = pattern.matcher(version.getName());
            if (matcher.matches()) {
                int num = Integer.parseInt(matcher.group(1));
                maxNum = Math.max(maxNum, num);
            }
        }

        return baseName + " (" + (maxNum + 1) + ")";
    }

    @Override
    @Transactional
    public void markAsTemplate(Long versionId, Boolean isTemplate) {
        WorkflowVersion version = new WorkflowVersion();
        version.setId(versionId);
        version.setIsTemplate(isTemplate ? 1 : 0);
        workflowVersionMapper.updateById(version);
    }

    @Override
    public WorkflowLineageDTO getLineageTree(Long versionId) {
        Set<Long> visited = new HashSet<>();
        return buildLineageTree(versionId, visited, 0);
    }

    /**
     * 递归构建溯源树
     */
    private WorkflowLineageDTO buildLineageTree(Long versionId, Set<Long> visited, int depth) {
        if (versionId == null || visited.contains(versionId) || depth > 10) {
            WorkflowLineageDTO dto = new WorkflowLineageDTO();
            dto.setIsCircular(visited.contains(versionId));
            return dto;
        }

        visited.add(versionId);

        WorkflowVersion version = workflowVersionMapper.selectById(versionId);
        if (version == null) {
            return null;
        }

        WorkflowLineageDTO dto = new WorkflowLineageDTO();
        dto.setId(version.getId());
        dto.setName(version.getName());
        dto.setVersion(version.getVersion());
        dto.setCreatedAt(version.getCreatedAt());

        // 查询创建人姓名
        if (version.getCreatorId() != null) {
            // TODO: 从user表查询创建人姓名
            dto.setCreatorName("User-" + version.getCreatorId());
        }

        // 递归查询源工作流
        if (version.getSourceVersionId() != null) {
            dto.setSource(buildLineageTree(version.getSourceVersionId().longValue(), visited, depth + 1));
        }

        return dto;
    }

    /**
     * 生成下一个版本号
     */
    private String generateNextVersion(Long projectId) {
        List<WorkflowVersion> versions = workflowVersionMapper.selectList(
            new LambdaQueryWrapper<WorkflowVersion>()
                .eq(WorkflowVersion::getProjectId, projectId)
                .orderByDesc(WorkflowVersion::getId)
                .last("LIMIT 1")
        );

        if (versions.isEmpty()) {
            return "v1.0";
        }

        String lastVersion = versions.get(0).getVersion();
        Pattern pattern = Pattern.compile("v(\\d+)\\.(\\d+)");
        Matcher matcher = pattern.matcher(lastVersion);
        
        if (matcher.matches()) {
            int major = Integer.parseInt(matcher.group(1));
            int minor = Integer.parseInt(matcher.group(2));
            return "v" + major + "." + (minor + 1);
        }

        return "v1.0";
    }

    /**
     * 创建审计日志
     */
    private void createAuditLog(Long workflowVersionId, String action, Long operatorId, String operatorName,
                               Map<String, Object> details, String ipAddress, String userAgent) {
        WorkflowAuditLog log = new WorkflowAuditLog(workflowVersionId, action, operatorId, operatorName);
        log.setDetails(details);
        log.setIpAddress(ipAddress);
        log.setUserAgent(userAgent);
        log.setCreatedAt(LocalDateTime.now());
        
        auditLogMapper.insert(log);
    }
}
