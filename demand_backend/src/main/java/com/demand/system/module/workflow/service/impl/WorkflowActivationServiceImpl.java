package com.demand.system.module.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.requirement.entity.RequirementTypeConfig;
import com.demand.system.module.requirement.mapper.RequirementTypeMapper;
import com.demand.system.module.workflow.dto.WorkflowValidationIssue;
import com.demand.system.module.workflow.dto.WorkflowVersionDTO;
import com.demand.system.module.workflow.engine.WorkflowGraphCompiler;
import com.demand.system.module.workflow.engine.WorkflowGraphValidator;
import com.demand.system.module.workflow.engine.WorkflowStateProjector;
import com.demand.system.module.workflow.entity.WorkflowApproval;
import com.demand.system.module.workflow.entity.WorkflowEdge;
import com.demand.system.module.workflow.entity.WorkflowHistory;
import com.demand.system.module.workflow.entity.WorkflowNode;
import com.demand.system.module.workflow.entity.WorkflowNodePermission;
import com.demand.system.module.workflow.entity.WorkflowVersion;
import com.demand.system.module.workflow.mapper.WorkflowApprovalMapper;
import com.demand.system.module.workflow.mapper.WorkflowEdgeMapper;
import com.demand.system.module.workflow.mapper.WorkflowHistoryMapper;
import com.demand.system.module.workflow.mapper.WorkflowNodeMapper;
import com.demand.system.module.workflow.mapper.WorkflowNodePermissionMapper;
import com.demand.system.module.workflow.mapper.WorkflowVersionMapper;
import com.demand.system.module.workflow.service.WorkflowActivationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WorkflowActivationServiceImpl implements WorkflowActivationService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WorkflowActivationServiceImpl.class);

    private final WorkflowVersionMapper workflowVersionMapper;
    private final WorkflowNodeMapper workflowNodeMapper;
    private final WorkflowEdgeMapper workflowEdgeMapper;
    private final WorkflowNodePermissionMapper workflowNodePermissionMapper;
    private final WorkflowApprovalMapper workflowApprovalMapper;
    private final RequirementTypeMapper requirementTypeMapper;
    private final WorkflowGraphValidator workflowGraphValidator;
    private final WorkflowGraphCompiler workflowGraphCompiler;
    private final WorkflowStateProjector workflowStateProjector;
    private final WorkflowHistoryMapper workflowHistoryMapper;
    private final ObjectMapper objectMapper;

    public WorkflowActivationServiceImpl(WorkflowVersionMapper workflowVersionMapper,
                                         WorkflowNodeMapper workflowNodeMapper,
                                         WorkflowEdgeMapper workflowEdgeMapper,
                                         WorkflowNodePermissionMapper workflowNodePermissionMapper,
                                         WorkflowApprovalMapper workflowApprovalMapper,
                                         RequirementTypeMapper requirementTypeMapper,
                                         WorkflowGraphValidator workflowGraphValidator,
                                         WorkflowGraphCompiler workflowGraphCompiler,
                                         WorkflowStateProjector workflowStateProjector,
                                         WorkflowHistoryMapper workflowHistoryMapper,
                                         ObjectMapper objectMapper) {
        this.workflowVersionMapper = workflowVersionMapper;
        this.workflowNodeMapper = workflowNodeMapper;
        this.workflowEdgeMapper = workflowEdgeMapper;
        this.workflowNodePermissionMapper = workflowNodePermissionMapper;
        this.workflowApprovalMapper = workflowApprovalMapper;
        this.requirementTypeMapper = requirementTypeMapper;
        this.workflowGraphValidator = workflowGraphValidator;
        this.workflowGraphCompiler = workflowGraphCompiler;
        this.workflowStateProjector = workflowStateProjector;
        this.workflowHistoryMapper = workflowHistoryMapper;
        this.objectMapper = objectMapper;
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

        workflowNodePermissionMapper.delete(new LambdaQueryWrapper<WorkflowNodePermission>()
                .eq(WorkflowNodePermission::getWorkflowVersionId, versionId));
        for (WorkflowNodePermission permission : compiled.permissions()) {
            workflowNodePermissionMapper.insert(permission);
        }

        workflowStateProjector.project(version.getProjectId(), nodes, compiled);

        version.setDefinition(compiled.definitionJson());
        version.setRuntimeHash(compiled.runtimeHash());
        version.setConfigHash(compiled.configHash());
        version.setIsActive(1);
        version.setActivationStatus("active");
        version.setActivatedAt(LocalDateTime.now());
        version.setUpdatedAt(LocalDateTime.now());
        workflowVersionMapper.updateById(version);
        recordHistory(version, "activate",
                "启用 V" + (version.getVersion() != null ? version.getVersion() : "") + "（" + version.getName() + "）");

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
        version.setUpdatedAt(LocalDateTime.now());
        workflowVersionMapper.updateById(version);
        recordHistory(version, "deactivate",
                "停用 V" + (version.getVersion() != null ? version.getVersion() : "") + "（" + version.getName() + "）");

        // 工作流禁用 → 联动禁用所有绑定该版本的需求类型；绑定关系保持不变，恢复由需求类型自行启用
        requirementTypeMapper.update(null, new LambdaUpdateWrapper<RequirementTypeConfig>()
                .eq(RequirementTypeConfig::getWorkflowVersionId, versionId)
                .set(RequirementTypeConfig::getEnabled, false));

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

    // ==== 修改历史记录辅助方法 ====

    private void recordHistory(WorkflowVersion version, String action, String summary) {
        if (version == null || version.getId() == null) {
            return;
        }
        try {
            WorkflowHistory history = new WorkflowHistory();
            history.setWorkflowVersionId(version.getId());
            history.setProjectId(version.getProjectId());
            history.setOperatorId(SecurityUtils.getCurrentUserId());
            history.setAction(action);
            history.setChangeSummary(summary);
            history.setVersionSnapshot(buildVersionSnapshot(version));
            workflowHistoryMapper.insert(history);
        } catch (Exception e) {
            log.warn("记录工作流启用历史失败，versionId={}, action={}", version.getId(), action, e);
        }
    }

    private String buildVersionSnapshot(WorkflowVersion version) {
        try {
            Long nodeCount = workflowNodeMapper.selectCount(new LambdaQueryWrapper<WorkflowNode>()
                    .eq(WorkflowNode::getWorkflowVersionId, version.getId()));
            Long edgeCount = workflowEdgeMapper.selectCount(new LambdaQueryWrapper<WorkflowEdge>()
                    .eq(WorkflowEdge::getWorkflowVersionId, version.getId()));
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("nodes", nodeCount == null ? 0 : nodeCount);
            snapshot.put("edges", edgeCount == null ? 0 : edgeCount);
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            return "{}";
        }
    }
}
