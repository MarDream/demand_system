package com.demand.system.module.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.workflow.dto.WorkflowVersionVO;
import com.demand.system.module.workflow.entity.WorkflowApproval;
import com.demand.system.module.workflow.entity.WorkflowRequirementType;
import com.demand.system.module.workflow.entity.WorkflowInstance;
import com.demand.system.module.workflow.entity.WorkflowVersion;
import com.demand.system.module.workflow.mapper.WorkflowApprovalMapper;
import com.demand.system.module.workflow.mapper.WorkflowRequirementTypeMapper;
import com.demand.system.module.workflow.mapper.WorkflowInstanceMapper;
import com.demand.system.module.workflow.mapper.WorkflowVersionMapper;
import com.demand.system.module.project.entity.Project;
import com.demand.system.module.project.mapper.ProjectMapper;
import com.demand.system.module.user.entity.User;
import com.demand.system.module.user.mapper.UserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 工作流版本管理服务
 */
@Service
public class WorkflowVersionService {

    private final WorkflowVersionMapper workflowVersionMapper;
    private final WorkflowRequirementTypeMapper requirementTypeMapper;
    private final WorkflowInstanceMapper workflowInstanceMapper;
    private final WorkflowApprovalMapper workflowApprovalMapper;
    private final WorkflowActivationService workflowActivationService;
    private final ProjectMapper projectMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    public WorkflowVersionService(WorkflowVersionMapper workflowVersionMapper,
                                  WorkflowRequirementTypeMapper requirementTypeMapper,
                                  WorkflowInstanceMapper workflowInstanceMapper,
                                  WorkflowApprovalMapper workflowApprovalMapper,
                                  WorkflowActivationService workflowActivationService,
                                  ProjectMapper projectMapper,
                                  UserMapper userMapper,
                                  ObjectMapper objectMapper) {
        this.workflowVersionMapper = workflowVersionMapper;
        this.requirementTypeMapper = requirementTypeMapper;
        this.workflowInstanceMapper = workflowInstanceMapper;
        this.workflowApprovalMapper = workflowApprovalMapper;
        this.workflowActivationService = workflowActivationService;
        this.projectMapper = projectMapper;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 发布工作流新版本
     *
     * @param projectId 项目ID
     * @param requirementTypeId 工单类型ID
     * @param definition 工作流定义JSON
     * @param changeLog 变更说明
     * @return 版本ID
     */
    @Transactional
    public Long publishWorkflow(Long projectId, Long requirementTypeId, String definition, String changeLog) {
        // 1. 计算新定义的哈希
        String newRuntimeHash = computeRuntimeHash(definition);
        String newConfigHash = computeConfigHash(definition);

        // 2. 查询当前活跃版本
        WorkflowVersion currentActive = getActiveVersionByType(requirementTypeId);

        // 3. 检查是否实质性变更（configHash 包含：节点、边、执行顺序，不包含名称、描述）
        if (currentActive != null && currentActive.getConfigHash() != null
                && currentActive.getConfigHash().equals(newConfigHash)) {
            // 仅名称/描述修改，更新当前版本（不生成新版本）
            String name = extractWorkflowName(definition);
            workflowVersionMapper.update(null, new LambdaUpdateWrapper<WorkflowVersion>()
                    .eq(WorkflowVersion::getId, currentActive.getId())
                    .set(WorkflowVersion::getName, name)
                    .set(WorkflowVersion::getDefinition, definition)
                    .set(WorkflowVersion::getRuntimeHash, newRuntimeHash));
            return currentActive.getId();
        }

        // 4. 实质性变更：创建新版本并提交审批
        String newVersion = generateNextVersion(projectId, requirementTypeId);
        String name = extractWorkflowName(definition);

        WorkflowVersion newWorkflowVersion = new WorkflowVersion();
        newWorkflowVersion.setProjectId(projectId);
        newWorkflowVersion.setVersion(newVersion);
        newWorkflowVersion.setName(name);
        newWorkflowVersion.setDefinition(definition);
        newWorkflowVersion.setRuntimeHash(newRuntimeHash);
        newWorkflowVersion.setConfigHash(newConfigHash);
        newWorkflowVersion.setIsActive(0); // 新版本默认未启用
        newWorkflowVersion.setActivationStatus("pending_approval"); // 待审批
        newWorkflowVersion.setSubmittedForApprovalAt(LocalDateTime.now());
        newWorkflowVersion.setChangeLog(changeLog);
        newWorkflowVersion.setCreatorId(SecurityUtils.getCurrentUserId());
        workflowVersionMapper.insert(newWorkflowVersion);

        return newWorkflowVersion.getId();
    }

    /**
     * 审批工作流版本
     *
     * @param versionId 版本ID
     * @param approved 是否通过
     * @param comment 审批意见
     */
    @Transactional
    public void approveWorkflow(Long versionId, Boolean approved, String comment) {
        Long operatorId = SecurityUtils.getCurrentUserId();

        // 1. 检查审批权限
        if (!hasApprovalPermission(operatorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "您没有工作流审批权限");
        }

        // 2. 查询待审批版本
        WorkflowVersion version = workflowVersionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "工作流版本不存在");
        }
        if (!"pending_approval".equals(version.getActivationStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该版本不在待审批状态");
        }

        // 3. 审批驳回
        if (!approved) {
            workflowVersionMapper.update(null, new LambdaUpdateWrapper<WorkflowVersion>()
                    .eq(WorkflowVersion::getId, versionId)
                    .set(WorkflowVersion::getActivationStatus, "rejected")
                    .set(WorkflowVersion::getApprovedBy, operatorId)
                    .set(WorkflowVersion::getApprovedAt, LocalDateTime.now())
                    .set(WorkflowVersion::getApprovalComment, comment));
            return;
        }

        // 4. 审批通过：先创建审批记录，再走完整激活管道（编译+权限写入+状态投射），保证运行时数据完整。
        //    工作流允许多个版本同时启用，具体使用关系由需求类型绑定决定。
        WorkflowRequirementType requirementType = findRequirementTypeByVersion(versionId);

        // 4a. 创建审批记录（activate() 会校验最新审批记录必须为 approved）
        WorkflowApproval approval = new WorkflowApproval();
        approval.setWorkflowVersionId(versionId);
        approval.setSubmitterId(version.getCreatorId());
        approval.setApproverId(operatorId);
        approval.setStatus("approved");
        approval.setComment(comment);
        approval.setSubmittedAt(version.getSubmittedForApprovalAt());
        approval.setApprovedAt(LocalDateTime.now());
        workflowApprovalMapper.insert(approval);

        // 4b. 走完整激活管道：编译 definitionJson、写入节点权限、投射 workflow_states
        workflowActivationService.activate(versionId);

        // 4c. 补充审批信息到版本记录（activate() 已设置 isActive/activationStatus/activatedAt 等）
        workflowVersionMapper.update(null, new LambdaUpdateWrapper<WorkflowVersion>()
                .eq(WorkflowVersion::getId, versionId)
                .set(WorkflowVersion::getApprovedBy, operatorId)
                .set(WorkflowVersion::getApprovedAt, LocalDateTime.now())
                .set(WorkflowVersion::getApprovalComment, comment));

        // 5. 更新工单类型绑定
        if (requirementType != null) {
            requirementTypeMapper.update(null, new LambdaUpdateWrapper<WorkflowRequirementType>()
                    .eq(WorkflowRequirementType::getId, requirementType.getId())
                    .set(WorkflowRequirementType::getWorkflowVersionId, versionId));
        }
    }

    /**
     * 查询工作流版本列表（系统维度，按时间倒序）
     *
     * @param projectId 项目ID（可选）
     * @return 版本列表
     */
    public List<WorkflowVersionVO> listVersions(Long projectId) {
        LambdaQueryWrapper<WorkflowVersion> queryWrapper = new LambdaQueryWrapper<WorkflowVersion>()
                .orderByDesc(WorkflowVersion::getCreatedAt);

        if (projectId != null) {
            queryWrapper.eq(WorkflowVersion::getProjectId, projectId);
        }

        List<WorkflowVersion> versions = workflowVersionMapper.selectList(queryWrapper);

        // 批量查询关联数据
        Set<Long> projectIds = versions.stream().map(WorkflowVersion::getProjectId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> creatorIds = versions.stream().map(WorkflowVersion::getCreatorId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> approverIds = versions.stream().map(WorkflowVersion::getApprovedBy)
                .filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, Project> projectMap = projectIds.isEmpty() ? Collections.emptyMap() :
                projectMapper.selectBatchIds(projectIds).stream()
                        .collect(Collectors.toMap(Project::getId, p -> p));

        Set<Long> allUserIds = new HashSet<>();
        allUserIds.addAll(creatorIds);
        allUserIds.addAll(approverIds);
        Map<Long, User> userMap = allUserIds.isEmpty() ? Collections.emptyMap() :
                userMapper.selectBatchIds(allUserIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));

        // 统计运行中实例数（一次性 GROUP BY 查询，避免 N+1）
        Map<Long, Long> instanceCountMap = new HashMap<>();
        List<Long> versionIds = versions.stream()
                .map(WorkflowVersion::getId)
                .filter(Objects::nonNull)
                .toList();
        if (!versionIds.isEmpty()) {
            for (Map<String, Object> row : workflowInstanceMapper.countRunningByVersionIds(versionIds)) {
                Object versionIdObj = row.get("workflowVersionId");
                Object cntObj = row.get("cnt");
                if (versionIdObj instanceof Number v && cntObj instanceof Number c) {
                    instanceCountMap.put(v.longValue(), c.longValue());
                }
            }
        }

        return versions.stream().map(version -> toVO(version, projectMap, userMap, instanceCountMap))
                .collect(Collectors.toList());
    }

    /**
     * 获取当前活跃版本（根据工单类型）
     */
    public WorkflowVersion getActiveVersionByType(Long requirementTypeId) {
        WorkflowRequirementType requirementType = requirementTypeMapper.selectOne(
                new LambdaQueryWrapper<WorkflowRequirementType>()
                        .eq(WorkflowRequirementType::getId, requirementTypeId));

        if (requirementType == null || requirementType.getWorkflowVersionId() == null) {
            return null;
        }

        return workflowVersionMapper.selectById(requirementType.getWorkflowVersionId());
    }

    /**
     * 计算运行时哈希（包含所有内容）
     */
    private String computeRuntimeHash(String definition) {
        return DigestUtils.md5DigestAsHex(definition.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算配置哈希（排除名称、描述，用于判定实质性变更）
     */
    private String computeConfigHash(String definition) {
        try {
            JsonNode root = objectMapper.readTree(definition);

            // 提取关键字段：nodes（去除name/label）、edges
            JsonNode nodes = root.path("nodes");
            JsonNode edges = root.path("edges");

            // 构造精简的配置对象
            Map<String, Object> config = new HashMap<>();
            List<Map<String, Object>> simplifiedNodes = new ArrayList<>();

            if (nodes.isArray()) {
                for (JsonNode node : nodes) {
                    Map<String, Object> nodeConfig = new HashMap<>();
                    nodeConfig.put("id", node.path("id").asText());
                    nodeConfig.put("type", node.path("type").asText());
                    nodeConfig.put("position", node.path("position").toString());
                    nodeConfig.put("properties", node.path("properties").toString());
                    simplifiedNodes.add(nodeConfig);
                }
            }

            config.put("nodes", simplifiedNodes);
            config.put("edges", edges.toString());

            String configJson = objectMapper.writeValueAsString(config);
            return DigestUtils.md5DigestAsHex(configJson.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            // 降级：使用完整哈希
            return computeRuntimeHash(definition);
        }
    }

    /**
     * 提取工作流名称
     */
    private String extractWorkflowName(String definition) {
        try {
            JsonNode root = objectMapper.readTree(definition);
            JsonNode nameNode = root.path("name");
            return nameNode.isTextual() ? nameNode.asText() : "未命名工作流";
        } catch (Exception e) {
            return "未命名工作流";
        }
    }

    /**
     * 生成下一个版本号
     */
    private String generateNextVersion(Long projectId, Long requirementTypeId) {
        List<WorkflowVersion> existingVersions = workflowVersionMapper.selectList(
                new LambdaQueryWrapper<WorkflowVersion>()
                        .eq(WorkflowVersion::getProjectId, projectId)
                        .orderByDesc(WorkflowVersion::getCreatedAt)
                        .last("LIMIT 1"));

        if (existingVersions.isEmpty()) {
            return "v1.0.0";
        }

        String lastVersion = existingVersions.get(0).getVersion();
        return incrementVersion(lastVersion);
    }

    /**
     * 版本号自增（语义化版本）
     */
    private String incrementVersion(String version) {
        // v1.0.0 -> v1.1.0
        if (version == null || !version.startsWith("v")) {
            return "v1.0.0";
        }

        String[] parts = version.substring(1).split("\\.");
        if (parts.length != 3) {
            return "v1.0.0";
        }

        try {
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]) + 1;
            return String.format("v%d.%d.0", major, minor);
        } catch (NumberFormatException e) {
            return "v1.0.0";
        }
    }

    /**
     * 查询工单类型（根据版本ID）
     */
    private WorkflowRequirementType findRequirementTypeByVersion(Long versionId) {
        return requirementTypeMapper.selectOne(
                new LambdaQueryWrapper<WorkflowRequirementType>()
                        .eq(WorkflowRequirementType::getWorkflowVersionId, versionId)
                        .last("LIMIT 1"));
    }

    /**
     * 检查审批权限（按钮权限标签）
     * 通过角色判断，由前端按钮权限控制
     */
    private boolean hasApprovalPermission(Long userId) {
        // 检查用户是否拥有 SUPER_ADMIN 角色
        // 前端通过 workflow:approve 权限标签控制按钮显示
        return SecurityUtils.hasAnyRole("SUPER_ADMIN");
    }

    /**
     * 转换为VO
     */
    private WorkflowVersionVO toVO(WorkflowVersion version, Map<Long, Project> projectMap,
                                   Map<Long, User> userMap, Map<Long, Long> instanceCountMap) {
        WorkflowVersionVO vo = new WorkflowVersionVO();
        vo.setId(version.getId());
        vo.setProjectId(version.getProjectId());
        vo.setVersion(version.getVersion());
        vo.setName(version.getName());
        vo.setIsActive(version.getIsActive());
        vo.setActivationStatus(version.getActivationStatus());
        vo.setActivationStatusName(resolveActivationStatusName(version.getActivationStatus()));
        vo.setActivatedAt(version.getActivatedAt());
        vo.setDeprecatedAt(version.getDeprecatedAt());
        vo.setChangeLog(version.getChangeLog());
        vo.setSubmittedForApprovalAt(version.getSubmittedForApprovalAt());
        vo.setApprovedAt(version.getApprovedAt());
        vo.setApprovedBy(version.getApprovedBy());
        vo.setApprovalComment(version.getApprovalComment());
        vo.setCreatorId(version.getCreatorId());
        vo.setCreatedAt(version.getCreatedAt());
        vo.setRunningInstanceCount(instanceCountMap.getOrDefault(version.getId(), 0L));

        if (version.getProjectId() != null) {
            Project project = projectMap.get(version.getProjectId());
            if (project != null) {
                vo.setProjectName(project.getName());
            }
        }

        if (version.getCreatorId() != null) {
            User creator = userMap.get(version.getCreatorId());
            if (creator != null) {
                vo.setCreatorName(StringUtils.hasText(creator.getRealName())
                        ? creator.getRealName() : creator.getUsername());
            }
        }

        if (version.getApprovedBy() != null) {
            User approver = userMap.get(version.getApprovedBy());
            if (approver != null) {
                vo.setApprovedByName(StringUtils.hasText(approver.getRealName())
                        ? approver.getRealName() : approver.getUsername());
            }
        }

        return vo;
    }

    /**
     * 解析激活状态名称
     */
    private String resolveActivationStatusName(String status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case "draft" -> "草稿";
            case "pending_approval" -> "待审批";
            case "approved" -> "已审批";
            case "active" -> "已启用";
            case "deprecated" -> "已废弃";
            case "rejected" -> "审批驳回";
            default -> status;
        };
    }
}
