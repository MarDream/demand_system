package com.demand.system.module.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.workflow.dto.WorkflowApprovalDTO;
import com.demand.system.module.workflow.dto.WorkflowConfigDTO;
import com.demand.system.module.workflow.dto.WorkflowEdgeDTO;
import com.demand.system.module.workflow.dto.WorkflowNodeDTO;
import com.demand.system.module.workflow.dto.WorkflowVersionMetaUpdateDTO;
import com.demand.system.module.workflow.dto.WorkflowVersionDTO;
import com.demand.system.module.workflow.engine.WorkflowVersionResolver;
import com.demand.system.module.workflow.entity.WorkflowApproval;
import com.demand.system.module.workflow.entity.WorkflowEdge;
import com.demand.system.module.workflow.entity.WorkflowNode;
import com.demand.system.module.workflow.entity.WorkflowVersion;
import com.demand.system.module.workflow.mapper.WorkflowApprovalMapper;
import com.demand.system.module.workflow.mapper.WorkflowEdgeMapper;
import com.demand.system.module.workflow.mapper.WorkflowNodeMapper;
import com.demand.system.module.workflow.mapper.WorkflowVersionMapper;
import com.demand.system.module.workflow.service.WorkflowConfigService;
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
import java.util.List;
import java.util.stream.Collectors;


@Service
public class WorkflowConfigServiceImpl implements WorkflowConfigService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WorkflowConfigServiceImpl.class);
    private static final String GLOBAL_WORKFLOW_NAME = "全局流程";

    private final WorkflowVersionMapper workflowVersionMapper;
    private final WorkflowNodeMapper workflowNodeMapper;
    private final WorkflowEdgeMapper workflowEdgeMapper;
    private final WorkflowApprovalMapper workflowApprovalMapper;
    private final ProjectMapper projectMapper;
    private final SysUserMapper sysUserMapper;

    public WorkflowConfigServiceImpl(WorkflowVersionMapper workflowVersionMapper, WorkflowNodeMapper workflowNodeMapper,
                                   WorkflowEdgeMapper workflowEdgeMapper, WorkflowApprovalMapper workflowApprovalMapper,
                                   ProjectMapper projectMapper, SysUserMapper sysUserMapper) {
        this.workflowVersionMapper = workflowVersionMapper;
        this.workflowNodeMapper = workflowNodeMapper;
        this.workflowEdgeMapper = workflowEdgeMapper;
        this.workflowApprovalMapper = workflowApprovalMapper;
        this.projectMapper = projectMapper;
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public WorkflowConfigDTO getWorkflowConfig(Long projectId) {
        Long normalizedProjectId = normalizeProjectId(projectId);
        // 获取当前激活的版本
        LambdaQueryWrapper<WorkflowVersion> versionQuery = new LambdaQueryWrapper<>();
        versionQuery.eq(WorkflowVersion::getProjectId, normalizedProjectId)
                .eq(WorkflowVersion::getIsActive, 1)
                .orderByDesc(WorkflowVersion::getVersion)
                .last("LIMIT 1");

        WorkflowVersion activeVersion = workflowVersionMapper.selectOne(versionQuery);

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

        Long normalizedProjectId = normalizeProjectId(projectId);
        WorkflowVersion draftVersion = findLatestInactiveVersion(normalizedProjectId);

        // 已提交待审核的版本不能继续覆盖，保存时应创建新的草稿版本
        if (draftVersion == null || hasPendingApproval(draftVersion.getId())) {
            draftVersion = createDraftVersion(normalizedProjectId, currentUserId);
        }

        // 删除旧的节点和连线
        deleteVersionConfig(draftVersion.getId());

        // 保存新的节点
        if (configDTO.getNodes() != null && !configDTO.getNodes().isEmpty()) {
            for (WorkflowNodeDTO nodeDTO : configDTO.getNodes()) {
                WorkflowNode node = new WorkflowNode();
                BeanUtils.copyProperties(nodeDTO, node);
                node.setWorkflowVersionId(draftVersion.getId());
                workflowNodeMapper.insert(node);
            }
        }

        // 保存新的连线
        if (configDTO.getEdges() != null && !configDTO.getEdges().isEmpty()) {
            for (WorkflowEdgeDTO edgeDTO : configDTO.getEdges()) {
                WorkflowEdge edge = new WorkflowEdge();
                BeanUtils.copyProperties(edgeDTO, edge);
                edge.setWorkflowVersionId(draftVersion.getId());
                workflowEdgeMapper.insert(edge);
            }
        }

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
        query.eq(WorkflowVersion::getProjectId, normalizedProjectId)
                .orderByDesc(WorkflowVersion::getVersion);

        List<WorkflowVersion> versions = workflowVersionMapper.selectList(query);

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

        Integer targetVersion = updateDTO.getVersion();
        if (targetVersion == null || targetVersion < 1) {
            throw new BusinessException("版本号必须大于0");
        }

        LambdaQueryWrapper<WorkflowVersion> duplicateQuery = new LambdaQueryWrapper<>();
        duplicateQuery.eq(WorkflowVersion::getProjectId, version.getProjectId())
                .eq(WorkflowVersion::getVersion, targetVersion)
                .ne(WorkflowVersion::getId, versionId);
        Long duplicateCount = workflowVersionMapper.selectCount(duplicateQuery);
        if (duplicateCount != null && duplicateCount > 0) {
            throw new BusinessException("版本号 V" + targetVersion + " 已存在，请重新输入");
        }

        version.setVersion(targetVersion);
        version.setName(normalizedName);
        workflowVersionMapper.updateById(version);

        return toVersionDTO(version);
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

        // 激活该版本
        WorkflowVersion version = workflowVersionMapper.selectById(approval.getWorkflowVersionId());
        if (version != null) {
            // 将该项目的其他版本设为非激活
            LambdaUpdateWrapper<WorkflowVersion> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(WorkflowVersion::getProjectId, version.getProjectId())
                    .set(WorkflowVersion::getIsActive, 0);
            workflowVersionMapper.update(null, updateWrapper);

            // 激活当前版本
            version.setIsActive(1);
            workflowVersionMapper.updateById(version);

            log.info("审核通过并激活工作流版本，versionId={}, projectId={}", version.getId(), version.getProjectId());
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

    private Integer getMaxVersion(Long projectId) {
        LambdaQueryWrapper<WorkflowVersion> query = new LambdaQueryWrapper<>();
        query.eq(WorkflowVersion::getProjectId, projectId)
                .orderByDesc(WorkflowVersion::getVersion)
                .last("LIMIT 1");

        WorkflowVersion maxVersion = workflowVersionMapper.selectOne(query);
        return maxVersion != null ? maxVersion.getVersion() : 0;
    }

    private WorkflowVersion findLatestInactiveVersion(Long projectId) {
        LambdaQueryWrapper<WorkflowVersion> versionQuery = new LambdaQueryWrapper<>();
        versionQuery.eq(WorkflowVersion::getProjectId, projectId)
                .eq(WorkflowVersion::getIsActive, 0)
                .orderByDesc(WorkflowVersion::getVersion)
                .last("LIMIT 1");
        return workflowVersionMapper.selectOne(versionQuery);
    }

    private boolean hasPendingApproval(Long versionId) {
        LambdaQueryWrapper<WorkflowApproval> approvalQuery = new LambdaQueryWrapper<>();
        approvalQuery.eq(WorkflowApproval::getWorkflowVersionId, versionId)
                .eq(WorkflowApproval::getStatus, "pending");
        return workflowApprovalMapper.selectCount(approvalQuery) > 0;
    }

    private WorkflowVersion createDraftVersion(Long projectId, Long currentUserId) {
        Integer maxVersion = getMaxVersion(projectId);
        WorkflowVersion draftVersion = new WorkflowVersion();
        draftVersion.setProjectId(projectId);
        draftVersion.setVersion(maxVersion + 1);
        draftVersion.setName("草稿版本 v" + (maxVersion + 1));
        draftVersion.setDefinition("{}");
        draftVersion.setIsActive(0);
        draftVersion.setCreatorId(currentUserId);
        draftVersion.setCreatedAt(LocalDateTime.now());
        workflowVersionMapper.insert(draftVersion);
        return draftVersion;
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
