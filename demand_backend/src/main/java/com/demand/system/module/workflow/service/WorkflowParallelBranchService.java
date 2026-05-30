package com.demand.system.module.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.workflow.dto.ParallelBranchVO;
import com.demand.system.module.workflow.engine.WorkflowConditionEvaluator;
import com.demand.system.module.workflow.engine.WorkflowGraphContext;
import com.demand.system.module.workflow.engine.WorkflowGraphNavigator;
import com.demand.system.module.workflow.entity.WorkflowEdge;
import com.demand.system.module.workflow.entity.WorkflowInstance;
import com.demand.system.module.workflow.entity.WorkflowNode;
import com.demand.system.module.workflow.entity.WorkflowParallelBranch;
import com.demand.system.module.workflow.mapper.WorkflowInstanceMapper;
import com.demand.system.module.workflow.mapper.WorkflowParallelBranchMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class WorkflowParallelBranchService {

    private final WorkflowParallelBranchMapper parallelBranchMapper;
    private final WorkflowInstanceMapper instanceMapper;
    private final WorkflowGraphNavigator graphNavigator;
    private final WorkflowConditionEvaluator conditionEvaluator;

    public WorkflowParallelBranchService(WorkflowParallelBranchMapper parallelBranchMapper,
                                           WorkflowInstanceMapper instanceMapper,
                                           WorkflowGraphNavigator graphNavigator,
                                           WorkflowConditionEvaluator conditionEvaluator) {
        this.parallelBranchMapper = parallelBranchMapper;
        this.instanceMapper = instanceMapper;
        this.graphNavigator = graphNavigator;
        this.conditionEvaluator = conditionEvaluator;
    }

    public List<ParallelBranchVO> listByRequirementId(Long requirementId) {
        WorkflowInstance instance = getInstance(requirementId);
        if (instance == null) {
            return List.of();
        }
        return parallelBranchMapper.selectList(
                new LambdaQueryWrapper<WorkflowParallelBranch>()
                        .eq(WorkflowParallelBranch::getInstanceId, instance.getId())
                        .orderByAsc(WorkflowParallelBranch::getId)
        ).stream().map(this::toVo).toList();
    }

    @Transactional
    public void initParallelBranchesIfNeeded(WorkflowInstance instance, WorkflowGraphContext context,
                                             String enteredBranchNodeId, Requirement requirement) {
        if (instance == null || context == null || !StringUtils.hasText(enteredBranchNodeId)) {
            return;
        }
        Optional<String> forkNodeId = findParallelForkForBranchEntry(context, enteredBranchNodeId);
        if (forkNodeId.isEmpty()) {
            return;
        }
        String parallelNodeId = forkNodeId.get();
        if (hasParallelBranches(instance.getId(), parallelNodeId)) {
            activateBranch(instance, enteredBranchNodeId);
            return;
        }

        WorkflowNode forkNode = context.getNode(parallelNodeId);
        List<BranchEntry> entries = resolveBranchEntries(context, forkNode, requirement);
        if (entries.size() < 2) {
            return;
        }

        Long activeBranchId = null;
        for (BranchEntry entry : entries) {
            WorkflowParallelBranch branch = new WorkflowParallelBranch();
            branch.setInstanceId(instance.getId());
            branch.setParallelNodeId(parallelNodeId);
            branch.setBranchNodeId(entry.nodeId());
            branch.setBranchName(entry.name());
            branch.setCurrentNodeId(entry.nodeId());
            boolean active = enteredBranchNodeId.equals(entry.nodeId());
            branch.setStatus(active ? "running" : "pending");
            if (active) {
                branch.setStartedAt(LocalDateTime.now());
            }
            parallelBranchMapper.insert(branch);
            if (active) {
                activeBranchId = branch.getId();
            }
        }

        instanceMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<WorkflowInstance>()
                .eq(WorkflowInstance::getId, instance.getId())
                .set(WorkflowInstance::getParallelNodeId, parallelNodeId)
                .set(WorkflowInstance::getActiveParallelBranchId, activeBranchId));
        instance.setParallelNodeId(parallelNodeId);
        instance.setActiveParallelBranchId(activeBranchId);
    }

    @Transactional
    public void afterTransition(WorkflowInstance instance, WorkflowGraphContext context,
                                String fromNodeId, String toNodeId, Requirement requirement) {
        if (instance == null || !StringUtils.hasText(instance.getParallelNodeId())) {
            return;
        }
        WorkflowParallelBranch activeBranch = getActiveBranch(instance);
        if (activeBranch == null) {
            return;
        }

        activeBranch.setCurrentNodeId(toNodeId);
        parallelBranchMapper.updateById(activeBranch);

        WorkflowNode forkNode = context.getNode(instance.getParallelNodeId());
        if (isMergeParallelNode(context, toNodeId) || isBranchExit(context, instance.getParallelNodeId(), activeBranch.getBranchNodeId(), toNodeId)) {
            activeBranch.setStatus("completed");
            activeBranch.setCompletedAt(LocalDateTime.now());
            parallelBranchMapper.updateById(activeBranch);

            if (canProceedAfterParallel(instance.getId(), instance.getParallelNodeId(), forkNode)) {
                clearParallelState(instance);
                return;
            }
            activateNextPendingBranch(instance);
        }
    }

    @Transactional
    public void switchActiveBranch(Long requirementId, Long branchId) {
        WorkflowInstance instance = getRequiredInstance(requirementId);
        WorkflowParallelBranch branch = parallelBranchMapper.selectById(branchId);
        if (branch == null || !Objects.equals(branch.getInstanceId(), instance.getId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "并行分支不存在");
        }
        if ("completed".equals(branch.getStatus()) || "skipped".equals(branch.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该分支已结束，无法切换");
        }

        parallelBranchMapper.selectList(
                new LambdaQueryWrapper<WorkflowParallelBranch>()
                        .eq(WorkflowParallelBranch::getInstanceId, instance.getId())
                        .eq(WorkflowParallelBranch::getParallelNodeId, branch.getParallelNodeId())
        ).forEach(item -> {
            if (Objects.equals(item.getId(), branchId)) {
                if (!"running".equals(item.getStatus())) {
                    item.setStatus("running");
                    if (item.getStartedAt() == null) {
                        item.setStartedAt(LocalDateTime.now());
                    }
                    parallelBranchMapper.updateById(item);
                }
            } else if ("running".equals(item.getStatus())) {
                item.setStatus("pending");
                parallelBranchMapper.updateById(item);
            }
        });

        String currentNodeId = StringUtils.hasText(branch.getCurrentNodeId())
                ? branch.getCurrentNodeId() : branch.getBranchNodeId();
        instanceMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<WorkflowInstance>()
                .eq(WorkflowInstance::getId, instance.getId())
                .set(WorkflowInstance::getActiveParallelBranchId, branchId)
                .set(WorkflowInstance::getCurrentNodeId, currentNodeId));
    }

    public boolean canProceedAfterParallel(Long instanceId, String parallelNodeId, WorkflowNode parallelNode) {
        List<WorkflowParallelBranch> branches = parallelBranchMapper.selectList(
                new LambdaQueryWrapper<WorkflowParallelBranch>()
                        .eq(WorkflowParallelBranch::getInstanceId, instanceId)
                        .eq(WorkflowParallelBranch::getParallelNodeId, parallelNodeId)
        );
        if (branches.isEmpty()) {
            return false;
        }
        String parallelType = resolveParallelType(parallelNode);
        long completedCount = branches.stream().filter(b -> "completed".equals(b.getStatus())).count();
        if ("OR".equalsIgnoreCase(parallelType)) {
            return completedCount > 0;
        }
        return completedCount == branches.size();
    }

    public boolean hasPendingParallel(Long instanceId) {
        if (instanceId == null) {
            return false;
        }
        Long pending = parallelBranchMapper.selectCount(
                new LambdaQueryWrapper<WorkflowParallelBranch>()
                        .eq(WorkflowParallelBranch::getInstanceId, instanceId)
                        .in(WorkflowParallelBranch::getStatus, List.of("pending", "running"))
        );
        return pending != null && pending > 0;
    }

    private void activateBranch(WorkflowInstance instance, String branchNodeId) {
        WorkflowParallelBranch branch = parallelBranchMapper.selectOne(
                new LambdaQueryWrapper<WorkflowParallelBranch>()
                        .eq(WorkflowParallelBranch::getInstanceId, instance.getId())
                        .eq(WorkflowParallelBranch::getBranchNodeId, branchNodeId)
                        .last("LIMIT 1")
        );
        if (branch == null) {
            return;
        }
        switchActiveBranch(instance.getRequirementId(), branch.getId());
    }

    private void activateNextPendingBranch(WorkflowInstance instance) {
        WorkflowParallelBranch next = parallelBranchMapper.selectOne(
                new LambdaQueryWrapper<WorkflowParallelBranch>()
                        .eq(WorkflowParallelBranch::getInstanceId, instance.getId())
                        .eq(WorkflowParallelBranch::getParallelNodeId, instance.getParallelNodeId())
                        .eq(WorkflowParallelBranch::getStatus, "pending")
                        .orderByAsc(WorkflowParallelBranch::getId)
                        .last("LIMIT 1")
        );
        if (next == null) {
            return;
        }
        switchActiveBranch(instance.getRequirementId(), next.getId());
    }

    private void clearParallelState(WorkflowInstance instance) {
        instanceMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<WorkflowInstance>()
                .eq(WorkflowInstance::getId, instance.getId())
                .set(WorkflowInstance::getParallelNodeId, null)
                .set(WorkflowInstance::getActiveParallelBranchId, null));
        instance.setParallelNodeId(null);
        instance.setActiveParallelBranchId(null);
    }

    private boolean hasParallelBranches(Long instanceId, String parallelNodeId) {
        Long count = parallelBranchMapper.selectCount(
                new LambdaQueryWrapper<WorkflowParallelBranch>()
                        .eq(WorkflowParallelBranch::getInstanceId, instanceId)
                        .eq(WorkflowParallelBranch::getParallelNodeId, parallelNodeId)
        );
        return count != null && count > 0;
    }

    private WorkflowParallelBranch getActiveBranch(WorkflowInstance instance) {
        if (instance.getActiveParallelBranchId() == null) {
            return null;
        }
        return parallelBranchMapper.selectById(instance.getActiveParallelBranchId());
    }

    private Optional<String> findParallelForkForBranchEntry(WorkflowGraphContext context, String branchEntryNodeId) {
        for (WorkflowNode node : context.nodesById().values()) {
            if (!"parallel".equalsIgnoreCase(node.getNodeType())) {
                continue;
            }
            for (WorkflowEdge edge : context.outgoing(node.getNodeId())) {
                for (WorkflowNode waitNode : graphNavigator.resolveNextWaitNodes(context, edge.getTargetNodeId(), null)) {
                    if (branchEntryNodeId.equals(waitNode.getNodeId())) {
                        return Optional.of(node.getNodeId());
                    }
                }
            }
        }
        return Optional.empty();
    }

    private List<BranchEntry> resolveBranchEntries(WorkflowGraphContext context, WorkflowNode forkNode, Requirement requirement) {
        Map<String, BranchEntry> entries = new LinkedHashMap<>();
        List<Map<String, Object>> configuredBranches = readConfiguredBranches(forkNode);
        if (!configuredBranches.isEmpty()) {
            for (Map<String, Object> branch : configuredBranches) {
                Object branchId = branch.get("branchId");
                if (branchId == null || !StringUtils.hasText(branchId.toString())) {
                    continue;
                }
                if (!conditionEvaluator.evaluateStructuredCondition(branch.get("condition"), requirement)) {
                    continue;
                }
                String name = branch.get("branchName") != null ? branch.get("branchName").toString() : branchId.toString();
                entries.putIfAbsent(branchId.toString(), new BranchEntry(branchId.toString(), name));
            }
            return new ArrayList<>(entries.values());
        }

        for (WorkflowEdge edge : context.outgoing(forkNode.getNodeId())) {
            if (!conditionEvaluator.matches(edge, requirement)) {
                continue;
            }
            List<WorkflowNode> waitNodes = graphNavigator.resolveNextWaitNodes(context, edge.getTargetNodeId(), requirement);
            if (waitNodes.isEmpty()) {
                continue;
            }
            WorkflowNode waitNode = waitNodes.get(0);
            String label = StringUtils.hasText(edge.getLabel()) ? edge.getLabel() : waitNode.getNodeName();
            entries.putIfAbsent(waitNode.getNodeId(), new BranchEntry(waitNode.getNodeId(), label));
        }
        return new ArrayList<>(entries.values());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readConfiguredBranches(WorkflowNode forkNode) {
        if (forkNode == null || forkNode.getProperties() == null) {
            return List.of();
        }
        Object branches = forkNode.getProperties().get("branches");
        if (branches instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add((Map<String, Object>) map);
                }
            }
            return result;
        }
        return List.of();
    }

    private String resolveParallelType(WorkflowNode parallelNode) {
        if (parallelNode == null || parallelNode.getProperties() == null) {
            return "AND";
        }
        Object parallelType = parallelNode.getProperties().get("parallelType");
        return parallelType != null ? parallelType.toString() : "AND";
    }

    private boolean isMergeParallelNode(WorkflowGraphContext context, String nodeId) {
        WorkflowNode node = context.getNode(nodeId);
        return node != null && "parallel".equalsIgnoreCase(node.getNodeType())
                && context.incoming(nodeId).size() > 1;
    }

    private boolean isBranchExit(WorkflowGraphContext context, String forkNodeId, String branchEntryNodeId, String toNodeId) {
        if (toNodeId.equals(branchEntryNodeId)) {
            return false;
        }
        List<String> path = graphNavigator.resolvePathToWaitNode(context, branchEntryNodeId, toNodeId, null);
        return path.isEmpty();
    }

    private WorkflowInstance getInstance(Long requirementId) {
        return instanceMapper.selectOne(
                new LambdaQueryWrapper<WorkflowInstance>()
                        .eq(WorkflowInstance::getRequirementId, requirementId)
        );
    }

    private WorkflowInstance getRequiredInstance(Long requirementId) {
        WorkflowInstance instance = getInstance(requirementId);
        if (instance == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "工作流实例不存在");
        }
        return instance;
    }

    private ParallelBranchVO toVo(WorkflowParallelBranch branch) {
        ParallelBranchVO vo = new ParallelBranchVO();
        vo.setId(branch.getId());
        vo.setInstanceId(branch.getInstanceId());
        vo.setParallelNodeId(branch.getParallelNodeId());
        vo.setBranchNodeId(branch.getBranchNodeId());
        vo.setBranchName(branch.getBranchName());
        vo.setCurrentNodeId(branch.getCurrentNodeId());
        vo.setStatus(branch.getStatus());
        vo.setStartedAt(branch.getStartedAt());
        vo.setCompletedAt(branch.getCompletedAt());
        vo.setCreatedAt(branch.getCreatedAt());
        return vo;
    }

    private record BranchEntry(String nodeId, String name) {
    }
}
