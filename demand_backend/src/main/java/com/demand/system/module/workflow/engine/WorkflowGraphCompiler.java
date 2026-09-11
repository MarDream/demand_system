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
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class WorkflowGraphCompiler {

    public record FlattenedTransition(String fromNodeId, String toNodeId, String label) {
    }

    public record CompiledWorkflow(
            String definitionJson,
            String runtimeHash,
            String configHash,
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

        // 计算配置哈希（包含节点和连线的全量属性，用于检测配置变更）
        String configJson = buildConfigFingerprint(nodes, edges);
        String configHash = sha256(configJson);

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
                default:
                    // 处理人由运行时解析（CREATOR / PREV_APPROVER 等），无需落库
                    break;
            }
            // 字段权限：需求动态字段在该节点上的可见 / 可编辑 / 必填范围
            applyFieldPermissions(permission, node.getProperties());
            permissions.add(permission);
        }

        return new CompiledWorkflow(definitionJson, sha256(definitionJson), configHash, waitNodeIds, deduplicate(transitions), permissions);
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
            config.setProperties(node.getProperties());
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
            config.setCondition(edge.getCondition());
            config.setProperties(edge.getProperties());
            if (edge.getCondition() != null) {
                Object expr = edge.getCondition().get("expr");
                if (expr != null) {
                    config.setConditions(String.valueOf(expr));
                }
                Object defaultFlow = edge.getCondition().get("defaultFlow");
                if (defaultFlow instanceof Boolean b) {
                    config.setDefaultFlow(b);
                }
            }
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

    /**
     * 将节点上配置的动态字段权限写入节点权限记录。
     * <p>数据来自可视化编辑器的节点属性：
     * <pre>properties.fieldPermissions = { "visible": [...], "editable": [...], "required": [...] }</pre>
     * 兼容旧数据顶层的 visibleFields / editableFields / requiredFields。
     * <p>三类均为空表示「未配置」，落库为 null，运行时按宽松策略回退为全部可见可编辑。
     */
    @SuppressWarnings("unchecked")
    private void applyFieldPermissions(WorkflowNodePermission permission, Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return;
        }
        List<String> visible = Collections.emptyList();
        List<String> editable = Collections.emptyList();
        List<String> required = Collections.emptyList();

        Object fp = properties.get("fieldPermissions");
        if (fp instanceof Map<?, ?> fpMap) {
            visible = toStringList(fpMap.get("visible"));
            editable = toStringList(fpMap.get("editable"));
            required = toStringList(fpMap.get("required"));
        }
        if (visible.isEmpty() && editable.isEmpty() && required.isEmpty()) {
            visible = toStringList(properties.get("visibleFields"));
            editable = toStringList(properties.get("editableFields"));
            required = toStringList(properties.get("requiredFields"));
        }
        if (visible.isEmpty() && editable.isEmpty() && required.isEmpty()) {
            return;
        }
        // 可编辑/必填隐式可见，避免仅配 editable 却因 visible 缺失被隐藏
        List<String> mergedVisible = new ArrayList<>(new LinkedHashSet<>(visible));
        mergedVisible.addAll(editable);
        mergedVisible.addAll(required);
        permission.setVisibleFields(writeJson(dedup(mergedVisible)));
        permission.setEditableFields(editable.isEmpty() ? null : writeJson(dedup(editable)));
        permission.setRequiredFields(required.isEmpty() ? null : writeJson(dedup(required)));
    }

    private List<String> dedup(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        return raw.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new)).stream().toList();
    }

    private List<String> toStringList(Object raw) {
        if (raw == null) {
            return Collections.emptyList();
        }
        if (raw instanceof List<?> list) {
            return list.stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty() || "[]".equals(text)) {
            return Collections.emptyList();
        }
        try {
            List<?> parsed = objectMapper.readValue(text, List.class);
            return parsed == null ? Collections.emptyList() : toStringList(parsed);
        } catch (Exception ignore) {
            return java.util.Arrays.stream(text.replace("[", "").replace("]", "").replace("\"", "").split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
    }

    /**
     * 构建全量配置指纹 JSON，包含节点和连线的所有属性。
     * 用于检测节点属性变更（如 assigneeType、timeoutHours 等），
     * 这些属性不在 definitionJson 中但属于配置变更。
     */
    private String buildConfigFingerprint(List<WorkflowNode> nodes, List<WorkflowEdge> edges) {
        try {
            var configMap = new java.util.LinkedHashMap<String, Object>();
            var nodeList = new ArrayList<java.util.LinkedHashMap<String, Object>>();
            if (nodes != null) {
                for (WorkflowNode node : nodes) {
                    if (node == null) continue;
                    var m = new java.util.LinkedHashMap<String, Object>();
                    m.put("nodeId", node.getNodeId());
                    m.put("nodeName", node.getNodeName());
                    m.put("nodeType", node.getNodeType());
                    m.put("assigneeType", node.getAssigneeType());
                    m.put("assigneeRoleId", node.getAssigneeRoleId());
                    m.put("assigneeRoleGroupId", node.getAssigneeRoleGroupId());
                    m.put("assigneeOrgId", node.getAssigneeOrgId());
                    m.put("assigneeUserIds", node.getAssigneeUserIds());
                    m.put("timeoutHours", node.getTimeoutHours());
                    m.put("timeoutAction", node.getTimeoutAction());
                    m.put("properties", node.getProperties());
                    nodeList.add(m);
                }
            }
            configMap.put("nodes", nodeList);

            var edgeList = new ArrayList<java.util.LinkedHashMap<String, Object>>();
            if (edges != null) {
                for (WorkflowEdge edge : edges) {
                    if (edge == null) continue;
                    var m = new java.util.LinkedHashMap<String, Object>();
                    m.put("edgeId", edge.getEdgeId());
                    m.put("sourceNodeId", edge.getSourceNodeId());
                    m.put("targetNodeId", edge.getTargetNodeId());
                    m.put("label", edge.getLabel());
                    m.put("condition", edge.getCondition());
                    m.put("properties", edge.getProperties());
                    edgeList.add(m);
                }
            }
            configMap.put("edges", edgeList);

            return objectMapper.writeValueAsString(configMap);
        } catch (JsonProcessingException e) {
            throw new com.demand.system.common.exception.BusinessException("工作流配置指纹序列化失败");
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
