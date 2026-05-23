package com.demand.system.module.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.requirement.mapper.RequirementMapper;
import com.demand.system.module.workflow.dto.WorkflowMigrationReportDTO;
import com.demand.system.module.workflow.engine.WorkflowDefinitionEngine;
import com.demand.system.module.workflow.engine.WorkflowRuntimeLoader;
import com.demand.system.module.workflow.engine.WorkflowGraphContext;
import com.demand.system.module.workflow.entity.WorkflowInstance;
import com.demand.system.module.workflow.entity.WorkflowNode;
import com.demand.system.module.workflow.entity.WorkflowVersion;
import com.demand.system.module.workflow.mapper.WorkflowInstanceMapper;
import com.demand.system.module.workflow.mapper.WorkflowVersionMapper;
import com.demand.system.module.workflow.service.WorkflowRuntimeMigrationService;
import com.demand.system.module.workflow.support.WorkflowNodeUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class WorkflowRuntimeMigrationServiceImpl implements WorkflowRuntimeMigrationService {

    private final RequirementMapper requirementMapper;
    private final WorkflowInstanceMapper workflowInstanceMapper;
    private final WorkflowVersionMapper workflowVersionMapper;
    private final WorkflowDefinitionEngine workflowDefinitionEngine;
    private final WorkflowRuntimeLoader workflowRuntimeLoader;

    public WorkflowRuntimeMigrationServiceImpl(RequirementMapper requirementMapper,
                                               WorkflowInstanceMapper workflowInstanceMapper,
                                               WorkflowVersionMapper workflowVersionMapper,
                                               WorkflowDefinitionEngine workflowDefinitionEngine,
                                               WorkflowRuntimeLoader workflowRuntimeLoader) {
        this.requirementMapper = requirementMapper;
        this.workflowInstanceMapper = workflowInstanceMapper;
        this.workflowVersionMapper = workflowVersionMapper;
        this.workflowDefinitionEngine = workflowDefinitionEngine;
        this.workflowRuntimeLoader = workflowRuntimeLoader;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowMigrationReportDTO markLegacyRequirements() {
        WorkflowMigrationReportDTO report = new WorkflowMigrationReportDTO();
        List<Requirement> candidates = requirementMapper.selectList(new LambdaQueryWrapper<Requirement>()
                .eq(Requirement::getDeletedAt, 0)
                .isNull(Requirement::getWorkflowInstanceId)
                .and(w -> w.isNull(Requirement::getLegacyWorkflow).or().eq(Requirement::getLegacyWorkflow, false)));

        for (Requirement requirement : candidates) {
            if (Boolean.TRUE.equals(requirement.getIsDraft())) {
                report.setSkippedCount(report.getSkippedCount() + 1);
                continue;
            }
            if (workflowDefinitionEngine.hasActiveDefinition(requirement.getProjectId())) {
                report.setSkippedCount(report.getSkippedCount() + 1);
                continue;
            }
            requirementMapper.update(null, new LambdaUpdateWrapper<Requirement>()
                    .eq(Requirement::getId, requirement.getId())
                    .set(Requirement::getLegacyWorkflow, true));
            report.setMarkedLegacyCount(report.getMarkedLegacyCount() + 1);
        }
        return report;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowMigrationReportDTO backfillInstances() {
        WorkflowMigrationReportDTO report = new WorkflowMigrationReportDTO();
        List<Requirement> candidates = requirementMapper.selectList(new LambdaQueryWrapper<Requirement>()
                .eq(Requirement::getDeletedAt, 0)
                .eq(Requirement::getIsDraft, false)
                .isNull(Requirement::getWorkflowInstanceId)
                .and(w -> w.isNull(Requirement::getLegacyWorkflow).or().eq(Requirement::getLegacyWorkflow, false)));

        for (Requirement requirement : candidates) {
            if (!workflowDefinitionEngine.hasActiveDefinition(requirement.getProjectId())) {
                report.setSkippedCount(report.getSkippedCount() + 1);
                continue;
            }
            try {
                if (backfillOne(requirement)) {
                    report.setBackfilledInstanceCount(report.getBackfilledInstanceCount() + 1);
                } else {
                    report.setSkippedCount(report.getSkippedCount() + 1);
                }
            } catch (Exception ex) {
                report.getFailedRequirementIds().add(requirement.getId());
            }
        }
        return report;
    }

    private boolean backfillOne(Requirement requirement) {
        WorkflowVersion activeVersion = workflowVersionMapper.selectList(new LambdaQueryWrapper<WorkflowVersion>()
                        .eq(WorkflowVersion::getProjectId, requirement.getProjectId())
                        .eq(WorkflowVersion::getIsActive, 1))
                .stream()
                .findFirst()
                .orElse(null);
        if (activeVersion == null) {
            return false;
        }

        WorkflowGraphContext context = workflowRuntimeLoader.loadContext(activeVersion.getId());
        Optional<WorkflowNode> matched = context.nodesById().values().stream()
                .filter(node -> WorkflowNodeUtils.isWaitNode(node.getNodeType()))
                .filter(node -> !"start".equalsIgnoreCase(node.getNodeType()))
                .filter(node -> matchesRequirement(node, requirement))
                .findFirst();

        WorkflowNode targetNode = matched.orElseGet(() -> context.nodesById().values().stream()
                .filter(node -> "approval".equalsIgnoreCase(node.getNodeType()))
                .findFirst()
                .orElse(null));
        if (targetNode == null) {
            return false;
        }

        WorkflowInstance instance = new WorkflowInstance();
        instance.setRequirementId(requirement.getId());
        instance.setWorkflowVersionId(activeVersion.getId());
        instance.setCurrentNodeId(targetNode.getNodeId());
        instance.setPreviousNodeId(null);
        instance.setStatus("end".equalsIgnoreCase(targetNode.getNodeType()) ? "completed" : "running");
        instance.setLockVersion(0);
        workflowInstanceMapper.insert(instance);

        String nodeStatusCode = WorkflowNodeUtils.resolveNodeStatusCode(targetNode, false);
        LambdaUpdateWrapper<Requirement> update = new LambdaUpdateWrapper<Requirement>()
                .eq(Requirement::getId, requirement.getId())
                .set(Requirement::getWorkflowInstanceId, instance.getId())
                .set(Requirement::getStatus, targetNode.getNodeName())
                .set(Requirement::getLegacyWorkflow, false);
        if (StringUtils.hasText(nodeStatusCode)) {
            update.set(Requirement::getNodeStatus, nodeStatusCode);
        }
        requirementMapper.update(null, update);
        return true;
    }

    private boolean matchesRequirement(WorkflowNode node, Requirement requirement) {
        if (StringUtils.hasText(requirement.getNodeStatus())) {
            String code = WorkflowNodeUtils.resolveNodeStatusCode(node, false);
            if (StringUtils.hasText(code) && Objects.equals(code, requirement.getNodeStatus())) {
                return true;
            }
        }
        return StringUtils.hasText(requirement.getStatus())
                && Objects.equals(node.getNodeName(), requirement.getStatus());
    }
}
