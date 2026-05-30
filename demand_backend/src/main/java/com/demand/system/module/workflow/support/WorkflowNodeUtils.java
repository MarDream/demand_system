package com.demand.system.module.workflow.support;

import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.workflow.entity.WorkflowNode;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Set;

public final class WorkflowNodeUtils {

    public static final Set<String> WAIT_NODE_TYPES = Set.of("start", "approval", "end");
    public static final Set<String> PASS_THROUGH_NODE_TYPES = Set.of("condition", "cc", "parallel");

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
        // 动态验证：根据处理人类型检查对应的配置字段
        switch (assigneeType) {
            case "SPECIFIED_USER":
                return node.getAssigneeUserIds() != null && !node.getAssigneeUserIds().isEmpty();
            case "SPECIFIED_ROLE":
                return node.getAssigneeRoleId() != null;
            case "SPECIFIED_ROLE_GROUP":
                return node.getAssigneeRoleGroupId() != null;
            case "SPECIFIED_ORG":
                return node.getAssigneeOrgId() != null;
            case "PREV_APPROVER":
            case "CREATOR":
                return true;
            default:
                // 未来扩展：如果新增类型但未配置对应字段，通过 properties 扩展字段检查
                return hasValidAssigneeFromProperties(node, assigneeType);
        }
    }

    /**
     * 从 properties 中动态检查处理人配置，支持未来扩展
     */
    private static boolean hasValidAssigneeFromProperties(WorkflowNode node, String assigneeType) {
        if (node.getProperties() == null) {
            return false;
        }
        // 根据类型名称推断可能的字段名，如 SPECIFIED_DEPARTMENT -> assigneeDepartmentId
        String expectedKey = "assignee" + assigneeType.replace("SPECIFIED_", "").toLowerCase() + "Id";
        Object value = node.getProperties().get(expectedKey);
        if (value == null) {
            // 尝试驼峰命名
            String camelKey = convertToCamelCase(expectedKey);
            value = node.getProperties().get(camelKey);
        }
        return value != null;
    }

    private static String convertToCamelCase(String snakeCase) {
        if (snakeCase == null || snakeCase.isEmpty()) {
            return snakeCase;
        }
        StringBuilder result = new StringBuilder();
        boolean nextUpper = false;
        for (char c : snakeCase.toCharArray()) {
            if (c == '_') {
                nextUpper = true;
            } else {
                result.append(nextUpper ? Character.toUpperCase(c) : c);
                nextUpper = false;
            }
        }
        return result.toString();
    }
}
