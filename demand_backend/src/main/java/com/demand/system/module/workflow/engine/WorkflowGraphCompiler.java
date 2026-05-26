package com.demand.system.module.workflow.engine;

import com.demand.system.module.workflow.dto.EdgeDTO;
import com.demand.system.module.workflow.dto.NodeConfigDTO;
import com.demand.system.module.workflow.dto.WorkflowDefinitionDTO;
import com.demand.system.module.workflow.entity.WorkflowEdge;
import com.demand.system.module.workflow.entity.WorkflowNode;
import com.demand.system.module.workflow.entity.WorkflowNodePermission;
import com.demand.system.module.workflow.support.WorkflowNodeUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class WorkflowGraphCompiler {

    public record FlattenedTransition(String fromNodeId, String toNodeId, String label) {
    }

    public record CompiledWorkflow(
            String definitionJson,
            String runtimeHash,
            Set<String> waitNodeIds,
            List<FlattenedTransition> transitions,
            List<WorkflowNodePermission> permissions
    ) {
    }

    private final ObjectMapper objectMapper;

    public WorkflowGraphCompiler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CompiledWorkflow compile(Long workflowVersionId, List<WorkflowNode> nodes, List<WorkflowEdge> edges) {
        WorkflowGraphContext context = WorkflowGraphContext.from(nodes, edges);
        WorkflowDefinitionDTO definition = new WorkflowDefinitionDTO();
        definition.setName("visual-workflow");
        definition.setNodes(toNodeConfigs(nodes));
        definition.setEdges(toEdgeConfigs(edges));

        String definitionJson;
        try {
            definitionJson = objectMapper.writeValueAsString(definition);
        } catch (JsonProcessingException e) {
            throw new com.demand.system.common.exception.BusinessException("工作流定义编译失败");
        }

        Set<String> waitNodeIds = new LinkedHashSet<>();
        List<FlattenedTransition> transitions = new ArrayList<>();
        for (WorkflowNode node : nodes) {
            if (node != null && WorkflowNodeUtils.isWaitNode(node.getNodeType())) {
                waitNodeIds.add(node.getNodeId());
            }
        }
        for (WorkflowNode fromNode : nodes) {
            if (fromNode == null || !WorkflowNodeUtils.isWaitNode(fromNode.getNodeType())) {
                continue;
            }
            appendFlattenedTransitions(context, fromNode.getNodeId(), fromNode.getNodeId(), new LinkedHashSet<>(), transitions);
        }

        List<WorkflowNodePermission> permissions = new ArrayList<>();
        for (WorkflowNode node : nodes) {
            if (node == null || !StringUtils.hasText(node.getNodeId())) {
                continue;
            }
            WorkflowNodePermission permission = new WorkflowNodePermission();
            permission.setWorkflowVersionId(workflowVersionId);
            permission.setNodeId(node.getNodeId());
            String assigneeType = node.getAssigneeType();
            if (!StringUtils.hasText(assigneeType)) {
                permissions.add(permission);
                continue;
            }
            switch (assigneeType) {
                case "SPECIFIED_ROLE":
                    if (node.getAssigneeRoleId() != null) {
                        permission.setAllowedRoles("[\"ROLE_" + node.getAssigneeRoleId() + "\"]");
                    }
                    break;
                case "SPECIFIED_ROLE_GROUP":
                    // 角色组权限将在运行时动态解析
                    if (node.getAssigneeRoleGroupId() != null) {
                        permission.setAllowedRoles("[\"ROLE_GROUP_" + node.getAssigneeRoleGroupId() + "\"]");
                    }
                    break;
                case "SPECIFIED_ORG":
                    // 组织权限将在运行时动态解析
                    if (node.getAssigneeOrgId() != null) {
                        permission.setAllowedUsers("[\"ORG_" + node.getAssigneeOrgId() + "\"]");
                    }
                    break;
                case "SPECIFIED_USER":
                    if (node.getAssigneeUserIds() != null) {
                        permission.setAllowedUsers(writeJson(node.getAssigneeUserIds()));
                    }
                    break;
            }
            permissions.add(permission);
        }

        return new CompiledWorkflow(definitionJson, sha256(definitionJson), waitNodeIds, deduplicate(transitions), permissions);
    }

    private void appendFlattenedTransitions(WorkflowGraphContext context, String originWaitNodeId, String currentNodeId,
                                            Set<String> visited, List<FlattenedTransition> transitions) {
        if (!visited.add(currentNodeId)) {
            return;
        }
        WorkflowNode current = context.getNode(currentNodeId);
        if (current == null) {
            visited.remove(currentNodeId);
            return;
        }

        for (WorkflowEdge edge : context.outgoing(currentNodeId)) {
            WorkflowNode target = context.getNode(edge.getTargetNodeId());
            if (target == null) {
                continue;
            }
            if (WorkflowNodeUtils.isWaitNode(target.getNodeType()) && !originWaitNodeId.equals(target.getNodeId())) {
                transitions.add(new FlattenedTransition(originWaitNodeId, target.getNodeId(),
                        StringUtils.hasText(edge.getLabel()) ? edge.getLabel() : target.getNodeName()));
                continue;
            }
            appendFlattenedTransitions(context, originWaitNodeId, target.getNodeId(), visited, transitions);
        }
        visited.remove(currentNodeId);
    }

    private List<FlattenedTransition> deduplicate(List<FlattenedTransition> transitions) {
        Set<String> seen = new LinkedHashSet<>();
        List<FlattenedTransition> result = new ArrayList<>();
        for (FlattenedTransition transition : transitions) {
            String key = transition.fromNodeId() + "->" + transition.toNodeId();
            if (seen.add(key)) {
                result.add(transition);
            }
        }
        return result;
    }

    private List<NodeConfigDTO> toNodeConfigs(List<WorkflowNode> nodes) {
        List<NodeConfigDTO> configs = new ArrayList<>();
        if (nodes == null) {
            return configs;
        }
        int order = 1;
        for (WorkflowNode node : nodes) {
            if (node == null) {
                continue;
            }
            NodeConfigDTO config = new NodeConfigDTO();
            config.setNodeId(node.getNodeId());
            config.setName(node.getNodeName());
            config.setType(node.getNodeType());
            config.setSortOrder(order++);
            config.setIsFinal("end".equalsIgnoreCase(node.getNodeType()));
            configs.add(config);
        }
        return configs;
    }

    private List<EdgeDTO> toEdgeConfigs(List<WorkflowEdge> edges) {
        List<EdgeDTO> configs = new ArrayList<>();
        if (edges == null) {
            return configs;
        }
        for (WorkflowEdge edge : edges) {
            if (edge == null) {
                continue;
            }
            EdgeDTO config = new EdgeDTO();
            config.setSource(edge.getSourceNodeId());
            config.setTarget(edge.getTargetNodeId());
            config.setLabel(edge.getLabel());
            configs.add(config);
        }
        return configs;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            return String.valueOf(input.hashCode());
        }
    }
}
