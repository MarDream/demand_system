package com.demand.system.module.workflow.support;

import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.workflow.entity.WorkflowNode;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Set;

public final class WorkflowNodeUtils {

    public static final Set<String> WAIT_NODE_TYPES = Set.of("start", "approval", "end");
    public static final Set<String> PASS_THROUGH_NODE_TYPES = Set.of("condition", "cc");

    private WorkflowNodeUtils() {
    }

    public static boolean isWaitNode(String nodeType) {
        return nodeType != null && WAIT_NODE_TYPES.contains(nodeType.trim().toLowerCase());
    }

    public static boolean isPassThroughNode(String nodeType) {
        return nodeType != null && PASS_THROUGH_NODE_TYPES.contains(nodeType.trim().toLowerCase());
    }

    public static Object readProperty(WorkflowNode node, String key) {
        if (node == null || node.getProperties() == null || !StringUtils.hasText(key)) {
            return null;
        }
        Object direct = node.getProperties().get(key);
        if (direct != null) {
            return direct;
        }
        Object nestedProperties = node.getProperties().get("properties");
        if (nestedProperties instanceof Map<?, ?> nestedMap) {
            return nestedMap.get(key);
        }
        return null;
    }

    public static boolean readBooleanProperty(WorkflowNode node, String key, boolean defaultValue) {
        Object value = readProperty(node, key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue() != 0;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(text);
    }

    public static String resolveNodeStatusCode(WorkflowNode node) {
        return resolveNodeStatusCode(node, true);
    }

    public static String resolveNodeStatusCode(WorkflowNode node, boolean required) {
        if (node == null) {
            if (required) {
                throw new BusinessException(400, "流程节点不存在");
            }
            return null;
        }
        Object code = readProperty(node, "nodeStatusCode");
        if (code != null && StringUtils.hasText(code.toString())) {
            return code.toString().trim();
        }
        if ("start".equalsIgnoreCase(node.getNodeType())) {
            return "DRAFT";
        }
        if (required && isWaitNode(node.getNodeType()) && !"start".equalsIgnoreCase(node.getNodeType())) {
            throw new BusinessException(400, "节点「" + node.getNodeName() + "」未绑定节点状态");
        }
        return null;
    }

    public static boolean isProjectRequired(WorkflowNode node) {
        return readBooleanProperty(node, "projectRequired", false);
    }

    public static boolean hasValidAssignee(WorkflowNode node) {
        if (node == null || !"approval".equalsIgnoreCase(node.getNodeType())) {
            return true;
        }
        String assigneeType = node.getAssigneeType();
        if (!StringUtils.hasText(assigneeType)) {
            return false;
        }
        if ("SPECIFIED_USER".equals(assigneeType)) {
            return node.getAssigneeUserIds() != null && !node.getAssigneeUserIds().isEmpty();
        }
        if ("SPECIFIED_ROLE".equals(assigneeType)) {
            return node.getAssigneeRoleId() != null;
        }
        return false;
    }
}
