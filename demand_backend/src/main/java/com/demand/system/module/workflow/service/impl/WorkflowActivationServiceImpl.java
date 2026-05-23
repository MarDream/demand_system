package com.demand.system.module.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.workflow.dto.WorkflowValidationIssue;
import com.demand.system.module.workflow.dto.WorkflowVersionDTO;
import com.demand.system.module.workflow.engine.WorkflowGraphCompiler;
import com.demand.system.module.workflow.engine.WorkflowGraphValidator;
import com.demand.system.module.workflow.engine.WorkflowStateProjector;
import com.demand.system.module.workflow.entity.WorkflowApproval;
import com.demand.system.module.workflow.entity.WorkflowEdge;
import com.demand.system.module.workflow.entity.WorkflowNode;
import com.demand.system.module.workflow.entity.WorkflowNodePermission;
import com.demand.system.module.workflow.entity.WorkflowVersion;
import com.demand.system.module.workflow.mapper.WorkflowApprovalMapper;
import com.demand.system.module.workflow.mapper.WorkflowEdgeMapper;
import com.demand.system.module.workflow.mapper.WorkflowNodeMapper;
import com.demand.system.module.workflow.mapper.WorkflowNodePermissionMapper;
import com.demand.system.module.workflow.mapper.WorkflowVersionMapper;
import com.demand.system.module.workflow.service.WorkflowActivationService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class WorkflowActivationServiceImpl implements WorkflowActivationService {

    private final WorkflowVersionMapper workflowVersionMapper;
    private final WorkflowNodeMapper workflowNodeMapper;
    private final WorkflowEdgeMapper workflowEdgeMapper;
    private final WorkflowNodePermissionMapper workflowNodePermissionMapper;
    private final WorkflowApprovalMapper workflowApprovalMapper;
    private final WorkflowGraphValidator workflowGraphValidator;
    private final WorkflowGraphCompiler workflowGraphCompiler;
    private final WorkflowStateProjector workflowStateProjector;

    public WorkflowActivationServiceImpl(WorkflowVersionMapper workflowVersionMapper,
                                         WorkflowNodeMapper workflowNodeMapper,
                                         WorkflowEdgeMapper workflowEdgeMapper,
                                         WorkflowNodePermissionMapper workflowNodePermissionMapper,
                                         WorkflowApprovalMapper workflowApprovalMapper,
                                         WorkflowGraphValidator workflowGraphValidator,
                                         WorkflowGraphCompiler workflowGraphCompiler,
                                         WorkflowStateProjector workflowStateProjector) {
        this.workflowVersionMapper = workflowVersionMapper;
        this.workflowNodeMapper = workflowNodeMapper;
        this.workflowEdgeMapper = workflowEdgeMapper;
        this.workflowNodePermissionMapper = workflowNodePermissionMapper;
        this.workflowApprovalMapper = workflowApprovalMapper;
        this.workflowGraphValidator = workflowGraphValidator;
        this.workflowGraphCompiler = workflowGraphCompiler;
        this.workflowStateProjector = workflowStateProjector;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowVersionDTO activate(Long versionId) {
        WorkflowVersion version = requireVersion(versionId);
        WorkflowApproval latestApproval = getLatestApproval(versionId);
        if (latestApproval == null || !"approved".equalsIgnoreCase(latestApproval.getStatus())) {
            throw new BusinessException("仅已审核通过的工作流版本支持启用");
        }

        List<WorkflowNode> nodes = loadNodes(versionId);
        List<WorkflowEdge> edges = loadEdges(versionId);
        workflowGraphValidator.validateForActivationOrThrow(nodes, edges, version.getProjectId());

        WorkflowGraphCompiler.CompiledWorkflow compiled = workflowGraphCompiler.compile(versionId, nodes, edges);
        workflowVersionMapper.update(null, new LambdaUpdateWrapper<WorkflowVersion>()
                .eq(WorkflowVersion::getProjectId, version.getProjectId())
                .set(WorkflowVersion::getIsActive, 0)
                .set(WorkflowVersion::getActivationStatus, "inactive"));

        workflowNodePermissionMapper.delete(new LambdaQueryWrapper<WorkflowNodePermission>()
                .eq(WorkflowNodePermission::getWorkflowVersionId, versionId));
        for (WorkflowNodePermission permission : compiled.permissions()) {
            workflowNodePermissionMapper.insert(permission);
        }

        workflowStateProjector.project(version.getProjectId(), nodes, compiled);

        version.setDefinition(compiled.definitionJson());
        version.setRuntimeHash(compiled.runtimeHash());
        version.setIsActive(1);
        version.setActivationStatus("active");
        version.setActivatedAt(LocalDateTime.now());
        workflowVersionMapper.updateById(version);

        return toVersionDTO(version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowVersionDTO deactivate(Long versionId) {
        WorkflowVersion version = requireVersion(versionId);
        if (version.getIsActive() == null || version.getIsActive() != 1) {
            throw new BusinessException("该工作流当前未启用");
        }
        version.setIsActive(0);
        version.setActivationStatus("inactive");
        workflowVersionMapper.updateById(version);
        return toVersionDTO(version);
    }

    @Override
    public List<WorkflowValidationIssue> validateVersion(Long versionId) {
        WorkflowVersion version = requireVersion(versionId);
        return workflowGraphValidator.validateForActivation(loadNodes(versionId), loadEdges(versionId), version.getProjectId());
    }

    private WorkflowVersion requireVersion(Long versionId) {
        WorkflowVersion version = workflowVersionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException("工作流版本不存在");
        }
        return version;
    }

    private List<WorkflowNode> loadNodes(Long versionId) {
        return workflowNodeMapper.selectList(new LambdaQueryWrapper<WorkflowNode>()
                .eq(WorkflowNode::getWorkflowVersionId, versionId));
    }

    private List<WorkflowEdge> loadEdges(Long versionId) {
        return workflowEdgeMapper.selectList(new LambdaQueryWrapper<WorkflowEdge>()
                .eq(WorkflowEdge::getWorkflowVersionId, versionId));
    }

    private WorkflowApproval getLatestApproval(Long versionId) {
        return workflowApprovalMapper.selectOne(new LambdaQueryWrapper<WorkflowApproval>()
                .eq(WorkflowApproval::getWorkflowVersionId, versionId)
                .orderByDesc(WorkflowApproval::getSubmittedAt)
                .last("LIMIT 1"));
    }

    private WorkflowVersionDTO toVersionDTO(WorkflowVersion version) {
        WorkflowVersionDTO dto = new WorkflowVersionDTO();
        BeanUtils.copyProperties(version, dto);
        return dto;
    }
}
