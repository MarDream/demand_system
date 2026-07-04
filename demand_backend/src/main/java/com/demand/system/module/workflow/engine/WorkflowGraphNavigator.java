package com.demand.system.module.workflow.engine;

import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.workflow.entity.WorkflowEdge;
import com.demand.system.module.workflow.entity.WorkflowNode;
import com.demand.system.module.workflow.support.WorkflowNodeUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class WorkflowGraphNavigator {

    private final WorkflowConditionEvaluator conditionEvaluator;

    public WorkflowGraphNavigator(WorkflowConditionEvaluator conditionEvaluator) {
        this.conditionEvaluator = conditionEvaluator;
    }

    public List<WorkflowNode> resolveNextWaitNodes(WorkflowGraphContext context, String fromNodeId, Requirement requirement) {
        Set<String> visited = new LinkedHashSet<>();
        List<WorkflowNode> results = new ArrayList<>();
        Set<String> seenWaitNodes = new LinkedHashSet<>();
        collectWaitNodes(context, fromNodeId, requirement, visited, results, seenWaitNodes);
        return results;
    }

    public List<String> resolvePathToWaitNode(WorkflowGraphContext context, String fromNodeId, String targetWaitNodeId,
                                              Requirement requirement) {
        List<String> path = new ArrayList<>();
        if (!findPath(context, fromNodeId, targetWaitNodeId, requirement, new LinkedHashSet<>(), path)) {
            return List.of();
        }
        return path;
    }

    public List<WorkflowNode> resolveAvailableTargets(WorkflowGraphContext context, String currentNodeId, Requirement requirement) {
        WorkflowNode current = context.getNode(currentNodeId);
        if (current == null) {
            return List.of();
        }

        List<WorkflowNode> targets = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (WorkflowEdge edge : context.outgoing(currentNodeId)) {
            if (!conditionEvaluator.matches(edge, requirement)) {
                continue;
            }
            WorkflowNode directTarget = context.getNode(edge.getTargetNodeId());
            if (directTarget == null) {
                continue;
            }
            if (WorkflowNodeUtils.isWaitNode(directTarget.getNodeType())) {
                if (seen.add(directTarget.getNodeId())) {
                    targets.add(directTarget);
                }
                continue;
            }
            for (WorkflowNode waitNode : resolveNextWaitNodes(context, directTarget.getNodeId(), requirement)) {
                if (seen.add(waitNode.getNodeId())) {
                    targets.add(waitNode);
                }
            }
        }
        return targets;
    }

    private void collectWaitNodes(WorkflowGraphContext context, String nodeId, Requirement requirement,
                                  Set<String> visited, List<WorkflowNode> results, Set<String> seenWaitNodes) {
        if (!visited.add(nodeId)) {
            return;
        }
        WorkflowNode node = context.getNode(nodeId);
        if (node == null) {
            return;
        }

        if (WorkflowNodeUtils.isWaitNode(node.getNodeType()) && !"start".equalsIgnoreCase(node.getNodeType())) {
            if (seenWaitNodes.add(node.getNodeId())) {
                results.add(node);
            }
            return;
        }

        for (WorkflowEdge edge : context.outgoing(nodeId)) {
            if (!conditionEvaluator.matches(edge, requirement)) {
                continue;
            }
            collectWaitNodes(context, edge.getTargetNodeId(), requirement, visited, results, seenWaitNodes);
        }
    }

    private boolean findPath(WorkflowGraphContext context, String currentNodeId, String targetWaitNodeId,
                             Requirement requirement, Set<String> visited, List<String> path) {
        if (!visited.add(currentNodeId)) {
            return false;
        }
        path.add(currentNodeId);

        if (currentNodeId.equals(targetWaitNodeId)) {
            WorkflowNode target = context.getNode(targetWaitNodeId);
            return target != null && WorkflowNodeUtils.isWaitNode(target.getNodeType());
        }

        WorkflowNode current = context.getNode(currentNodeId);
        if (current != null && WorkflowNodeUtils.isWaitNode(current.getNodeType())
                && !"start".equalsIgnoreCase(current.getNodeType())
                && !currentNodeId.equals(targetWaitNodeId)) {
            path.remove(path.size() - 1);
            visited.remove(currentNodeId);
            return false;
        }

        for (WorkflowEdge edge : context.outgoing(currentNodeId)) {
            if (!conditionEvaluator.matches(edge, requirement)) {
                continue;
            }
            if (findPath(context, edge.getTargetNodeId(), targetWaitNodeId, requirement, visited, path)) {
                return true;
            }
        }

        path.remove(path.size() - 1);
        visited.remove(currentNodeId);
        return false;
    }

    /**
     * 从当前节点沿入边向前查找上一个 wait 节点（用于 previousNodeId 为空时的驳回目标定位）
     */
    public WorkflowNode resolveRollbackTarget(WorkflowGraphContext context, String currentNodeId, Requirement requirement) {
        Set<String> visited = new LinkedHashSet<>();
        return findPreviousWaitNode(context, currentNodeId, requirement, visited);
    }

    private WorkflowNode findPreviousWaitNode(WorkflowGraphContext context, String fromNodeId, Requirement requirement, Set<String> visited) {
        if (!visited.add(fromNodeId)) {
            return null;
        }
        for (WorkflowEdge edge : context.incoming(fromNodeId)) {
            if (!conditionEvaluator.matches(edge, requirement)) {
                continue;
            }
            WorkflowNode source = context.getNode(edge.getSourceNodeId());
            if (source == null) continue;

            if (WorkflowNodeUtils.isWaitNode(source.getNodeType()) && !"start".equalsIgnoreCase(source.getNodeType())) {
                return source;
            }
            // 通过 pass-through 节点继续向上查找
            if (WorkflowNodeUtils.isPassThroughNode(source.getNodeType())) {
                WorkflowNode found = findPreviousWaitNode(context, source.getNodeId(), requirement, visited);
                if (found != null) return found;
            }
        }
        // 如果当前节点是开始节点，返回开始节点
        WorkflowNode current = context.getNode(fromNodeId);
        if (current != null && "start".equalsIgnoreCase(current.getNodeType())) {
            return current;
        }
        return null;
    }
}
