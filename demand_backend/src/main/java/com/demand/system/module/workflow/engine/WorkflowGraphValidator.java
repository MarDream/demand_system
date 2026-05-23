package com.demand.system.module.workflow.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.requirement.mapper.RequirementMapper;
import com.demand.system.module.workflow.dto.WorkflowValidationIssue;
import com.demand.system.module.workflow.entity.WorkflowEdge;
import com.demand.system.module.workflow.entity.WorkflowNode;
import com.demand.system.module.workflow.support.WorkflowNodeUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

@Component
public class WorkflowGraphValidator {

    private final RequirementMapper requirementMapper;

    public WorkflowGraphValidator(RequirementMapper requirementMapper) {
        this.requirementMapper = requirementMapper;
    }

    public List<WorkflowValidationIssue> validate(List<WorkflowNode> nodes, List<WorkflowEdge> edges) {
        return validate(nodes, edges, null, false);
    }

    public List<WorkflowValidationIssue> validateForActivation(List<WorkflowNode> nodes, List<WorkflowEdge> edges,
                                                             Long projectId) {
        return validate(nodes, edges, projectId, true);
    }

    private List<WorkflowValidationIssue> validate(List<WorkflowNode> nodes, List<WorkflowEdge> edges,
                                                   Long projectId, boolean forActivation) {
        List<WorkflowValidationIssue> issues = new ArrayList<>();
        if (nodes == null || nodes.isEmpty()) {
            issues.add(issue("nodes", "工作流至少包含一个节点", "error"));
            return issues;
        }

        WorkflowGraphContext context = WorkflowGraphContext.from(nodes, edges);
        List<WorkflowNode> startNodes = nodes.stream()
                .filter(node -> "start".equalsIgnoreCase(node.getNodeType()))
                .toList();
        if (startNodes.isEmpty()) {
            issues.add(issue("nodes", "工作流缺少开始节点", "error"));
        } else if (startNodes.size() > 1) {
            issues.add(issue("nodes", "工作流只能有一个开始节点", "error"));
        }

        long endCount = nodes.stream().filter(node -> "end".equalsIgnoreCase(node.getNodeType())).count();
        if (endCount == 0) {
            issues.add(issue("nodes", "工作流至少包含一个结束节点", "error"));
        }

        WorkflowNode startNode = startNodes.isEmpty() ? null : startNodes.get(0);
        if (startNode != null && context.outgoing(startNode.getNodeId()).isEmpty()) {
            issues.add(issue("edges", "开始节点缺少后续连线", "error"));
        }

        for (WorkflowNode node : nodes) {
            if (node == null || !StringUtils.hasText(node.getNodeId())) {
                issues.add(issue("nodes", "存在未配置 nodeId 的节点", "error"));
                continue;
            }
            if ("approval".equalsIgnoreCase(node.getNodeType())) {
                if (!WorkflowNodeUtils.hasValidAssignee(node)) {
                    issues.add(issue("nodes/" + node.getNodeId(), "审批节点「" + node.getNodeName() + "」必须配置处理人", "error"));
                }
                if (!StringUtils.hasText(WorkflowNodeUtils.resolveNodeStatusCode(node, false))) {
                    issues.add(issue("nodes/" + node.getNodeId(), "审批节点「" + node.getNodeName() + "」必须绑定节点状态", "error"));
                }
            }
            if ("end".equalsIgnoreCase(node.getNodeType())
                    && !StringUtils.hasText(WorkflowNodeUtils.resolveNodeStatusCode(node, false))) {
                issues.add(issue("nodes/" + node.getNodeId(), "结束节点「" + node.getNodeName() + "」必须绑定节点状态", "error"));
            }
        }

        if (edges != null) {
            for (WorkflowEdge edge : edges) {
                if (edge == null) {
                    continue;
                }
                WorkflowNode source = context.getNode(edge.getSourceNodeId());
                WorkflowNode target = context.getNode(edge.getTargetNodeId());
                if (source != null && "condition".equalsIgnoreCase(source.getNodeType())) {
                    String expr = edge.getCondition() == null ? null : String.valueOf(edge.getCondition().get("expr"));
                    if (!StringUtils.hasText(expr)) {
                        issues.add(issue("edges/" + edge.getEdgeId(), "条件节点出边必须配置条件表达式", "error"));
                    }
                }
                if (source != null && "start".equalsIgnoreCase(source.getNodeType())
                        && target != null && "start".equalsIgnoreCase(target.getNodeType())) {
                    issues.add(issue("edges/" + edge.getEdgeId(), "开始节点不能指向开始节点", "error"));
                }
            }
        }

        if (startNode != null) {
            Set<String> reachable = reachableNodes(context, startNode.getNodeId());
            for (WorkflowNode node : nodes) {
                if (node == null || !StringUtils.hasText(node.getNodeId())) {
                    continue;
                }
                if (!reachable.contains(node.getNodeId())) {
                    issues.add(issue("nodes/" + node.getNodeId(), "节点「" + node.getNodeName() + "」无法从开始节点到达", "error"));
                }
            }
            if (endCount > 0 && reachable.stream().noneMatch(nodeId -> {
                WorkflowNode node = context.getNode(nodeId);
                return node != null && "end".equalsIgnoreCase(node.getNodeType());
            })) {
                issues.add(issue("nodes", "开始节点无法到达任何结束节点", "error"));
            }
        }

        if (forActivation && projectId != null && !hasErrors(issues)) {
            Set<String> supportedCodes = new HashSet<>();
            for (WorkflowNode node : nodes) {
                String code = WorkflowNodeUtils.resolveNodeStatusCode(node, false);
                if (StringUtils.hasText(code)) {
                    supportedCodes.add(code);
                }
            }
            supportedCodes.add("DRAFT");
            supportedCodes.add("CANCELLED");

            List<Requirement> activeRequirements = requirementMapper.selectList(new LambdaQueryWrapper<Requirement>()
                    .eq(Requirement::getProjectId, projectId)
                    .eq(Requirement::getDeletedAt, 0)
                    .eq(Requirement::getIsDraft, false));
            for (Requirement requirement : activeRequirements) {
                if (requirement.getWorkflowInstanceId() == null) {
                    continue;
                }
                String nodeStatus = requirement.getNodeStatus();
                if (StringUtils.hasText(nodeStatus) && !supportedCodes.contains(nodeStatus)) {
                    issues.add(issue("activation", "在途需求使用了新版本不支持的状态: " + nodeStatus, "error"));
                }
            }
        }

        return issues;
    }

    public void validateOrThrow(List<WorkflowNode> nodes, List<WorkflowEdge> edges) {
        validateOrThrow(validate(nodes, edges));
    }

    public void validateForActivationOrThrow(List<WorkflowNode> nodes, List<WorkflowEdge> edges, Long projectId) {
        validateOrThrow(validateForActivation(nodes, edges, projectId));
    }

    private void validateOrThrow(List<WorkflowValidationIssue> issues) {
        List<String> errors = issues.stream()
                .filter(issue -> "error".equalsIgnoreCase(issue.getSeverity()))
                .map(WorkflowValidationIssue::getMessage)
                .toList();
        if (!errors.isEmpty()) {
            throw new com.demand.system.common.exception.BusinessException(
                    400, String.join("；", errors), issues);
        }
    }

    private boolean hasErrors(List<WorkflowValidationIssue> issues) {
        return issues.stream().anyMatch(issue -> "error".equalsIgnoreCase(issue.getSeverity()));
    }

    private Set<String> reachableNodes(WorkflowGraphContext context, String startNodeId) {
        Set<String> reachable = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(startNodeId);
        while (!queue.isEmpty()) {
            String nodeId = queue.poll();
            if (!reachable.add(nodeId)) {
                continue;
            }
            for (WorkflowEdge edge : context.outgoing(nodeId)) {
                if (edge.getTargetNodeId() != null && !Objects.equals(edge.getSourceNodeId(), edge.getTargetNodeId())) {
                    queue.add(edge.getTargetNodeId());
                }
            }
        }
        return reachable;
    }

    private WorkflowValidationIssue issue(String path, String message, String severity) {
        return new WorkflowValidationIssue(path, message, severity);
    }
}
