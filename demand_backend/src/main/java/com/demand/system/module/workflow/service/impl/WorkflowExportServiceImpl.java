package com.demand.system.module.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.workflow.dto.WorkflowEdgeDTO;
import com.demand.system.module.workflow.dto.WorkflowExportDTO;
import com.demand.system.module.workflow.dto.WorkflowNodeDTO;
import com.demand.system.module.workflow.entity.WorkflowApproval;
import com.demand.system.module.workflow.entity.WorkflowEdge;
import com.demand.system.module.workflow.entity.WorkflowNode;
import com.demand.system.module.workflow.entity.WorkflowVersion;
import com.demand.system.module.workflow.mapper.WorkflowApprovalMapper;
import com.demand.system.module.workflow.mapper.WorkflowEdgeMapper;
import com.demand.system.module.workflow.mapper.WorkflowNodeMapper;
import com.demand.system.module.workflow.mapper.WorkflowVersionMapper;
import com.demand.system.module.workflow.service.WorkflowExportService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class WorkflowExportServiceImpl implements WorkflowExportService {

    private static final String EXPORT_VERSION = "1.0.0";

    private final WorkflowVersionMapper workflowVersionMapper;
    private final WorkflowNodeMapper workflowNodeMapper;
    private final WorkflowEdgeMapper workflowEdgeMapper;
    private final WorkflowApprovalMapper workflowApprovalMapper;

    public WorkflowExportServiceImpl(WorkflowVersionMapper workflowVersionMapper,
                                     WorkflowNodeMapper workflowNodeMapper,
                                     WorkflowEdgeMapper workflowEdgeMapper,
                                     WorkflowApprovalMapper workflowApprovalMapper) {
        this.workflowVersionMapper = workflowVersionMapper;
        this.workflowNodeMapper = workflowNodeMapper;
        this.workflowEdgeMapper = workflowEdgeMapper;
        this.workflowApprovalMapper = workflowApprovalMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public WorkflowExportDTO exportWorkflow(Long versionId) {
        WorkflowVersion version = workflowVersionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException("工作流版本不存在");
        }
        if (!isApprovedForExport(version)) {
            throw new BusinessException("只能导出审核通过的工作流");
        }

        WorkflowExportDTO.WorkflowConfigData config = new WorkflowExportDTO.WorkflowConfigData();
        config.setNodes(loadNodeDTOs(versionId));
        config.setEdges(loadEdgeDTOs(versionId));

        WorkflowExportDTO.WorkflowMetadata metadata = new WorkflowExportDTO.WorkflowMetadata();
        metadata.setOriginalVersionId(version.getId());
        metadata.setOriginalCreatedAt(version.getCreatedAt());
        metadata.setApprovedAt(resolveApprovedAt(version));
        metadata.setDescription(version.getChangeLog());

        WorkflowExportDTO.WorkflowData workflow = new WorkflowExportDTO.WorkflowData();
        workflow.setName(version.getName());
        workflow.setVersion(version.getVersion());
        workflow.setProjectId(version.getProjectId());
        workflow.setConfig(config);
        workflow.setMetadata(metadata);

        WorkflowExportDTO dto = new WorkflowExportDTO();
        dto.setExportVersion(EXPORT_VERSION);
        dto.setExportedAt(LocalDateTime.now());
        dto.setExportedBy(resolveExporterName());
        dto.setWorkflow(workflow);
        return dto;
    }

    private boolean isApprovedForExport(WorkflowVersion version) {
        if (version == null) {
            return false;
        }
        String activationStatus = version.getActivationStatus();
        if ("approved".equalsIgnoreCase(activationStatus) || "active".equalsIgnoreCase(activationStatus)) {
            return true;
        }
        WorkflowApproval latestApproval = getLatestApproval(version.getId());
        return latestApproval != null && "approved".equalsIgnoreCase(latestApproval.getStatus());
    }

    private LocalDateTime resolveApprovedAt(WorkflowVersion version) {
        if (version.getApprovedAt() != null) {
            return version.getApprovedAt();
        }
        WorkflowApproval latestApproval = getLatestApproval(version.getId());
        return latestApproval == null ? null : latestApproval.getApprovedAt();
    }

    private WorkflowApproval getLatestApproval(Long versionId) {
        return workflowApprovalMapper.selectOne(new LambdaQueryWrapper<WorkflowApproval>()
                .eq(WorkflowApproval::getWorkflowVersionId, versionId)
                .orderByDesc(WorkflowApproval::getSubmittedAt)
                .last("LIMIT 1"));
    }

    private List<WorkflowNodeDTO> loadNodeDTOs(Long versionId) {
        return workflowNodeMapper.selectList(new LambdaQueryWrapper<WorkflowNode>()
                        .eq(WorkflowNode::getWorkflowVersionId, versionId))
                .stream()
                .map(node -> {
                    WorkflowNodeDTO dto = new WorkflowNodeDTO();
                    BeanUtils.copyProperties(node, dto);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private List<WorkflowEdgeDTO> loadEdgeDTOs(Long versionId) {
        return workflowEdgeMapper.selectList(new LambdaQueryWrapper<WorkflowEdge>()
                        .eq(WorkflowEdge::getWorkflowVersionId, versionId))
                .stream()
                .map(edge -> {
                    WorkflowEdgeDTO dto = new WorkflowEdgeDTO();
                    BeanUtils.copyProperties(edge, dto);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private String resolveExporterName() {
        String username = SecurityUtils.getCurrentUsername();
        return StringUtils.hasText(username) ? username : "system";
    }
}
