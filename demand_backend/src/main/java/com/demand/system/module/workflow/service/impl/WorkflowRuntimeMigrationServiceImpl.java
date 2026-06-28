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
import com.demand.system.module.workflow.mapper.NodeStatusMapper;
import com.demand.system.module.workflow.mapper.WorkflowVersionMapper;
import com.demand.system.module.workflow.service.WorkflowRuntimeMigrationService;
import com.demand.system.module.workflow.support.WorkflowNodeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class WorkflowRuntimeMigrationServiceImpl implements WorkflowRuntimeMigrationService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowRuntimeMigrationServiceImpl.class);

    private final RequirementMapper requirementMapper;
    private final WorkflowInstanceMapper workflowInstanceMapper;
    private final WorkflowVersionMapper workflowVersionMapper;
    private final WorkflowDefinitionEngine workflowDefinitionEngine;
    private final WorkflowRuntimeLoader workflowRuntimeLoader;
    private final NodeStatusMapper nodeStatusMapper;

    /** 自注入：用于在循环中调用 @Transactional 方法时走 Spring 代理，确保每次对齐独立事务 */
    @Lazy
    private final WorkflowRuntimeMigrationService self;

    public WorkflowRuntimeMigrationServiceImpl(RequirementMapper requirementMapper,
                                               WorkflowInstanceMapper workflowInstanceMapper,
                                               WorkflowVersionMapper workflowVersionMapper,
                                               WorkflowDefinitionEngine workflowDefinitionEngine,
                                               WorkflowRuntimeLoader workflowRuntimeLoader,
                                               NodeStatusMapper nodeStatusMapper,
                                               @Lazy WorkflowRuntimeMigrationService self) {
        this.requirementMapper = requirementMapper;
        this.workflowInstanceMapper = workflowInstanceMapper;
        this.workflowVersionMapper = workflowVersionMapper;
        this.workflowDefinitionEngine = workflowDefinitionEngine;
        this.workflowRuntimeLoader = workflowRuntimeLoader;
        this.nodeStatusMapper = nodeStatusMapper;
        this.self = self;
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

    /**
     * 对齐所有运行中的工作流实例到活跃版本。
     * <p>外层不加事务：通过 self 代理调用 alignRequirementInstanceIfNeeded，
     * 每次对齐在独立事务中执行，避免大事务超时。
     */
    @Override
    public int alignRunningInstancesToActiveVersion() {
        // 分页查询，避免一次性加载大量数据
        int pageSize = 100;
        int pageNum = 0;
        int totalMigratedCount = 0;

        while (true) {
            List<Requirement> candidates = requirementMapper.selectList(new LambdaQueryWrapper<Requirement>()
                    .eq(Requirement::getDeletedAt, 0)
                    .eq(Requirement::getIsDraft, false)
                    .isNotNull(Requirement::getWorkflowInstanceId)
                    .last("LIMIT " + (pageNum * pageSize) + ", " + pageSize));

            if (candidates.isEmpty()) {
                break; // 没有更多数据
            }

            int batchMigratedCount = 0;
            for (Requirement requirement : candidates) {
                try {
                    // 通过 self 代理调用，走独立事务
                    if (self.alignRequirementInstanceIfNeeded(requirement.getId())) {
                        batchMigratedCount++;
                    }
                } catch (Exception e) {
                    log.warn("对齐需求实例失败: requirementId={}", requirement.getId(), e);
                    // 继续处理下一条，不中断整个任务
                }
            }

            totalMigratedCount += batchMigratedCount;
            pageNum++;

            // 如果当前批次已处理完毕且少于 pageSize，说明到达末尾
            if (candidates.size() < pageSize) {
                break;
            }
        }

        return totalMigratedCount;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean alignRequirementInstanceIfNeeded(Long requirementId) {
        if (requirementId == null) {
            return false;
        }
        Requirement requirement = requirementMapper.selectById(requirementId);
        if (requirement == null || requirement.getWorkflowInstanceId() == null || Boolean.TRUE.equals(requirement.getIsDraft())) {
            return false;
        }
        WorkflowInstance instance = workflowInstanceMapper.selectById(requirement.getWorkflowInstanceId());
        if (instance == null || !"running".equalsIgnoreCase(instance.getStatus())) {
            return false;
        }

        WorkflowVersion activeVersion = workflowVersionMapper.selectList(new LambdaQueryWrapper<WorkflowVersion>()
                        .eq(WorkflowVersion::getProjectId, requirement.getProjectId())
                        .eq(WorkflowVersion::getIsActive, 1))
                .stream()
                .findFirst()
                .orElse(null);
        if (activeVersion == null || Objects.equals(activeVersion.getId(), instance.getWorkflowVersionId())) {
            return false;
        }

        WorkflowGraphContext activeContext = workflowRuntimeLoader.loadContext(activeVersion.getId());
        WorkflowNode targetCurrentNode = resolveEquivalentNode(activeContext, requirement.getNodeStatus(), requirement.getStatus());
        if (targetCurrentNode == null) {
            return false;
        }

        WorkflowGraphContext oldContext = workflowRuntimeLoader.loadContext(instance.getWorkflowVersionId());
        WorkflowNode previousNode = StringUtils.hasText(instance.getPreviousNodeId())
                ? oldContext.getNode(instance.getPreviousNodeId())
                : null;
        WorkflowNode targetPreviousNode = previousNode == null
                ? null
                : resolveEquivalentNode(activeContext,
                WorkflowNodeUtils.resolveNodeStatusCode(previousNode, false),
                previousNode.getNodeName());

        workflowInstanceMapper.updateById(buildMigratedInstance(instance, activeVersion.getId(),
                targetCurrentNode.getNodeId(), targetPreviousNode == null ? null : targetPreviousNode.getNodeId()));

        String targetStatusCode = WorkflowNodeUtils.resolveNodeStatusCode(targetCurrentNode, false);
        if (StringUtils.hasText(targetStatusCode)) {
            requirementMapper.update(null, new LambdaUpdateWrapper<Requirement>()
                    .eq(Requirement::getId, requirement.getId())
                    .set(Requirement::getNodeStatus, targetStatusCode)
                    .set(Requirement::getStatus, resolveNodeStatusName(targetStatusCode)));
        }
        return true;
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

    private WorkflowInstance buildMigratedInstance(WorkflowInstance source, Long workflowVersionId,
                                                   String currentNodeId, String previousNodeId) {
        WorkflowInstance updated = new WorkflowInstance();
        updated.setId(source.getId());
        updated.setRequirementId(source.getRequirementId());
        updated.setWorkflowVersionId(workflowVersionId);
        updated.setCurrentNodeId(currentNodeId);
        updated.setPreviousNodeId(previousNodeId);
        updated.setStatus(source.getStatus());
        updated.setLockVersion(source.getLockVersion());
        updated.setCreatedAt(source.getCreatedAt());
        updated.setUpdatedAt(source.getUpdatedAt());
        return updated;
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

    private WorkflowNode resolveEquivalentNode(WorkflowGraphContext context, String nodeStatusCode, String nodeName) {
        if (context == null) {
            return null;
        }
        if (StringUtils.hasText(nodeStatusCode)) {
            Optional<WorkflowNode> matchedByStatus = context.nodesById().values().stream()
                    .filter(node -> WorkflowNodeUtils.isWaitNode(node.getNodeType()))
                    .filter(node -> !"start".equalsIgnoreCase(node.getNodeType()))
                    .filter(node -> Objects.equals(nodeStatusCode, WorkflowNodeUtils.resolveNodeStatusCode(node, false)))
                    .findFirst();
            if (matchedByStatus.isPresent()) {
                return matchedByStatus.get();
            }
        }
        if (StringUtils.hasText(nodeName)) {
            return context.nodesById().values().stream()
                    .filter(node -> WorkflowNodeUtils.isWaitNode(node.getNodeType()))
                    .filter(node -> !"start".equalsIgnoreCase(node.getNodeType()))
                    .filter(node -> Objects.equals(nodeName, node.getNodeName()))
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private String resolveNodeStatusName(String nodeStatusCode) {
        if (!StringUtils.hasText(nodeStatusCode)) {
            return "新建";
        }
        return nodeStatusMapper.selectList(null).stream()
                .filter(status -> Objects.equals(status.getCode(), nodeStatusCode))
                .map(status -> status.getName())
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(nodeStatusCode);
    }
}
