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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

        Map<String, Integer> nodeIdCount = new HashMap<>();
        for (WorkflowNode node : nodes) {
            if (node != null && StringUtils.hasText(node.getNodeId())) {
                nodeIdCount.merge(node.getNodeId(), 1, Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> entry : nodeIdCount.entrySet()) {
            if (entry.getValue() > 1) {
                issues.add(issue("nodes/" + entry.getKey(), "节点ID重复: " + entry.getKey(), "error",
                        "请检查画布中是否存在重复节点，删除重复节点或重新保存生成唯一ID"));
            }
        }

        WorkflowGraphContext context = WorkflowGraphContext.from(nodes, edges);
        List<WorkflowNode> startNodes = nodes.stream()
                .filter(node -> "start".equalsIgnoreCase(node.getNodeType()))
                .toList();
        if (startNodes.isEmpty()) {
            issues.add(issue("nodes", "工作流缺少开始节点", "error", "请在画布中添加一个开始节点"));
        } else if (startNodes.size() > 1) {
            issues.add(issue("nodes", "工作流只能有一个开始节点", "error", "请保留唯一开始节点，删除多余开始节点"));
        }

        long endCount = nodes.stream().filter(node -> "end".equalsIgnoreCase(node.getNodeType())).count();
        if (endCount == 0) {
            issues.add(issue("nodes", "工作流至少包含一个结束节点", "error", "请添加结束节点并确保流程可以到达该节点"));
        }

        WorkflowNode startNode = startNodes.isEmpty() ? null : startNodes.get(0);
        if (startNode != null && context.outgoing(startNode.getNodeId()).isEmpty()) {
            issues.add(issue("edges", "开始节点缺少后续连线", "error", "请从开始节点连接到第一个处理节点"));
        }

        for (WorkflowNode node : nodes) {
            if (node == null || !StringUtils.hasText(node.getNodeId())) {
                issues.add(issue("nodes", "存在未配置 nodeId 的节点", "error", "请删除异常节点后重新创建，或重新保存画布生成节点ID"));
                continue;
            }
            if (!StringUtils.hasText(node.getNodeType())) {
                issues.add(issue("nodes/" + node.getNodeId(), "节点「" + safeNodeName(node) + "」缺少节点类型", "error", "请重新选择节点类型或删除后重建该节点"));
            }
            if (!StringUtils.hasText(node.getNodeName())) {
                issues.add(issue("nodes/" + node.getNodeId() + "/nodeName", "节点存在空名称", "warning", "建议为节点填写清晰名称，方便审批人理解流程"));
            }
            if ("approval".equalsIgnoreCase(node.getNodeType())) {
                if (!WorkflowNodeUtils.hasValidAssignee(node)) {
                    issues.add(issue("nodes/" + node.getNodeId() + "/assignee", "审批节点「" + safeNodeName(node) + "」必须配置处理人", "error", "请在节点配置中选择处理人、角色、角色组、组织或动态处理人"));
                }
                if (!StringUtils.hasText(WorkflowNodeUtils.resolveNodeStatusCode(node, false))) {
                    issues.add(issue("nodes/" + node.getNodeId() + "/status", "审批节点「" + safeNodeName(node) + "」必须绑定节点状态", "error", "请为审批节点选择对应的需求状态"));
                }
                if (node.getTimeoutHours() != null && node.getTimeoutHours() <= 0) {
                    issues.add(issue("nodes/" + node.getNodeId() + "/timeoutHours", "审批节点「" + safeNodeName(node) + "」超时时长必须大于0", "warning", "请调整为合理的超时时长，或清空超时设置"));
                }
            }
            if ("condition".equalsIgnoreCase(node.getNodeType()) && context.outgoing(node.getNodeId()).size() < 2) {
                issues.add(issue("nodes/" + node.getNodeId(), "条件节点「" + safeNodeName(node) + "」建议至少配置两个分支", "warning", "请补充分支，或将该节点改为普通审批/处理节点"));
            }
            if ("end".equalsIgnoreCase(node.getNodeType())
                    && !StringUtils.hasText(WorkflowNodeUtils.resolveNodeStatusCode(node, false))) {
                issues.add(issue("nodes/" + node.getNodeId() + "/status", "结束节点「" + safeNodeName(node) + "」必须绑定节点状态", "error", "请为结束节点绑定终态，例如已完成、已拒绝或已关闭"));
            }
        }

        if (edges != null) {
            Set<String> edgeKeys = new HashSet<>();
            Set<String> conditionNodesWithDefault = new HashSet<>();
            for (WorkflowEdge edge : edges) {
                if (edge == null) {
                    continue;
                }
                if (!StringUtils.hasText(edge.getSourceNodeId())) {
                    issues.add(issue("edges/" + safeEdgeId(edge) + "/sourceNodeId", "存在未配置源节点的连线", "error", "请删除异常连线后重新连接节点"));
                    continue;
                }
                if (!StringUtils.hasText(edge.getTargetNodeId())) {
                    issues.add(issue("edges/" + safeEdgeId(edge) + "/targetNodeId", "存在未配置目标节点的连线", "error", "请删除异常连线后重新连接节点"));
                    continue;
                }
                WorkflowNode source = context.getNode(edge.getSourceNodeId());
                WorkflowNode target = context.getNode(edge.getTargetNodeId());
                if (source == null && !isTerminalStatus(edge.getSourceNodeId())) {
                    issues.add(issue("edges/" + safeEdgeId(edge) + "/sourceNodeId", "连线引用了不存在的源节点: " + edge.getSourceNodeId(), "error", "请删除该连线并从有效节点重新连接"));
                }
                if (target == null && !isTerminalStatus(edge.getTargetNodeId())) {
                    issues.add(issue("edges/" + safeEdgeId(edge) + "/targetNodeId", "连线引用了不存在的目标节点: " + edge.getTargetNodeId(), "error", "请删除该连线并连接到有效节点"));
                }
                String edgeKey = edge.getSourceNodeId() + "->" + edge.getTargetNodeId();
                if (!edgeKeys.add(edgeKey)) {
                    issues.add(issue("edges/" + safeEdgeId(edge), "存在重复连线: " + edgeKey, "warning", "建议删除重复连线，避免审批路径展示混乱"));
                }
                if (source != null && "condition".equalsIgnoreCase(source.getNodeType())) {
                    boolean defaultBranch = edge.getCondition() != null
                            && Boolean.TRUE.equals(edge.getCondition().get("defaultFlow"));
                    Object exprValue = edge.getCondition() == null ? null : edge.getCondition().get("expr");
                    String expr = exprValue == null ? null : String.valueOf(exprValue);
                    if (defaultBranch) {
                        conditionNodesWithDefault.add(source.getNodeId());
                    } else if (!StringUtils.hasText(expr)) {
                        issues.add(issue("edges/" + safeEdgeId(edge) + "/condition", "条件节点出边必须配置条件表达式或设置为默认分支", "error", "请为该分支配置条件表达式，或将其标记为默认分支"));
                    }
                }
                if (source != null && "start".equalsIgnoreCase(source.getNodeType())
                        && target != null && "start".equalsIgnoreCase(target.getNodeType())) {
                    issues.add(issue("edges/" + safeEdgeId(edge), "开始节点不能指向开始节点", "error", "请将开始节点连接到审批、条件或其他业务节点"));
                }
            }
            for (WorkflowNode node : nodes) {
                if (node != null && "condition".equalsIgnoreCase(node.getNodeType())
                        && !conditionNodesWithDefault.contains(node.getNodeId())) {
                    issues.add(issue("nodes/" + node.getNodeId(), "条件节点「" + safeNodeName(node) + "」缺少默认分支", "warning", "建议添加默认分支，避免所有条件均不满足时流程中断"));
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
                    issues.add(issue("nodes/" + node.getNodeId(), "节点「" + safeNodeName(node) + "」无法从开始节点到达", "error", "请添加从开始节点到该节点的路径，或删除该孤立节点"));
                }
            }
            if (endCount > 0 && reachable.stream().noneMatch(nodeId -> {
                WorkflowNode node = context.getNode(nodeId);
                return node != null && "end".equalsIgnoreCase(node.getNodeType());
            })) {
                issues.add(issue("nodes", "开始节点无法到达任何结束节点", "error", "请检查流程主路径，确保至少存在一条从开始到结束的完整路径"));
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
                    issues.add(issue("activation", "在途需求使用了新版本不支持的状态: " + nodeStatus, "error", "请保留该状态对应的节点，或先迁移/处理相关在途需求"));
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

    private String safeNodeName(WorkflowNode node) {
        if (node == null) {
            return "未知节点";
        }
        return StringUtils.hasText(node.getNodeName()) ? node.getNodeName() : node.getNodeId();
    }

    private String safeEdgeId(WorkflowEdge edge) {
        if (edge == null) {
            return "unknown";
        }
        return StringUtils.hasText(edge.getEdgeId())
                ? edge.getEdgeId()
                : String.valueOf(edge.getSourceNodeId()) + "_to_" + String.valueOf(edge.getTargetNodeId());
    }

    private boolean isTerminalStatus(String nodeId) {
        if (nodeId == null) {
            return false;
        }
        String lower = nodeId.toLowerCase();
        return "cancelled".equals(lower) || "accepted".equals(lower) || "rejected".equals(lower);
    }

    private WorkflowValidationIssue issue(String path, String message, String severity) {
        return issue(path, message, severity, null);
    }

    private WorkflowValidationIssue issue(String path, String message, String severity, String suggestion) {
        WorkflowValidationIssue issue = new WorkflowValidationIssue(path, message, severity);
        issue.setRuleCode(path == null ? null : path.replace('/', '.'));
        issue.setFieldPath(path);
        issue.setSuggestion(suggestion);
        issue.setBlocking("error".equalsIgnoreCase(severity));
        return issue;
    }
}