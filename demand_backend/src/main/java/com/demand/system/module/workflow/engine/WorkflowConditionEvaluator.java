package com.demand.system.module.workflow.engine;

import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.workflow.entity.WorkflowEdge;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WorkflowConditionEvaluator {

    private static final Pattern SIMPLE_EXPR = Pattern.compile("^\\s*([a-zA-Z_][\\w]*)\\s*(==|!=)\\s*['\"]([^'\"]*)['\"]\\s*$");

    public boolean matches(WorkflowEdge edge, Requirement requirement) {
        String expr = resolveExpression(edge);
        if (!StringUtils.hasText(expr)) {
            return true;
        }
        if (requirement == null) {
            return false;
        }

        Matcher matcher = SIMPLE_EXPR.matcher(expr.trim());
        if (!matcher.matches()) {
            return true;
        }

        String field = matcher.group(1);
        String operator = matcher.group(2);
        String expected = matcher.group(3);
        String actual = readRequirementField(requirement, field);
        if ("==".equals(operator)) {
            return Objects.equals(normalize(actual), normalize(expected));
        }
        return !Objects.equals(normalize(actual), normalize(expected));
    }

    private String resolveExpression(WorkflowEdge edge) {
        if (edge == null) {
            return null;
        }
        if (edge.getCondition() != null) {
            Object expr = edge.getCondition().get("expr");
            if (expr != null && StringUtils.hasText(expr.toString())) {
                return expr.toString();
            }
        }
        if (edge.getProperties() != null) {
            Object condition = edge.getProperties().get("condition");
            if (condition instanceof Map<?, ?> conditionMap) {
                Object expr = conditionMap.get("expr");
                if (expr != null && StringUtils.hasText(expr.toString())) {
                    return expr.toString();
                }
            }
        }
        return null;
    }

    private String readRequirementField(Requirement requirement, String field) {
        return switch (field) {
            case "priority" -> requirement.getPriority();
            case "type" -> requirement.getType();
            case "nodeStatus" -> requirement.getNodeStatus();
            case "status" -> requirement.getStatus();
            case "projectId" -> requirement.getProjectId() == null ? null : String.valueOf(requirement.getProjectId());
            default -> null;
        };
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
