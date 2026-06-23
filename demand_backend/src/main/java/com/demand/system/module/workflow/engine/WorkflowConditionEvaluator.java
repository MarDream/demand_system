package com.demand.system.module.workflow.engine;

import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.workflow.entity.WorkflowEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WorkflowConditionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(WorkflowConditionEvaluator.class);

    private static final Pattern SIMPLE_EXPR = Pattern.compile("^\\s*([a-zA-Z_][\\w]*)\\s*(==|!=)\\s*['\"]([^'\"]*)['\"]\\s*$");

    /** 正则表达式最大长度限制 */
    private static final int REGEX_MAX_LENGTH = 128;
    /** 需求字段值最大长度（防超长输入导致回溯爆炸） */
    private static final int FIELD_VALUE_MAX_LENGTH = 1024;
    /** 正则匹配超时（毫秒） */
    private static final long REGEX_TIMEOUT_MS = 2000;
    /** 允许的正则字符集（排除命名组 (?< 等复杂特性） */
    private static final Pattern SAFE_REGEX_CHARSET = Pattern.compile("^[\\^$.*+?\\[\\](){}|\\\\\\-a-zA-Z0-9_\\s]+$");

    /** 用于正则超时执行的线程池 */
    private static final ExecutorService REGEX_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "condition-regex-eval");
        t.setDaemon(true);
        return t;
    });

    public boolean matches(WorkflowEdge edge, Requirement requirement) {
        Map<String, Object> cond = resolveCondition(edge);
        if (cond == null || cond.isEmpty()) {
            return true;
        }
        // 优先结构化评估（rules + logic）
        Object rulesObj = cond.get("rules");
        if (rulesObj instanceof List<?> rules && !rules.isEmpty()) {
            String logic = String.valueOf(cond.getOrDefault("logic", "AND"));
            return evaluateRules(rules, requirement, logic);
        }
        // 兼容旧 expr 单条表达式
        Object exprObj = cond.get("expr");
        if (exprObj != null && StringUtils.hasText(exprObj.toString())) {
            return evaluateSimpleExpr(exprObj.toString().trim(), requirement);
        }
        return true;
    }

    /**
     * 结构化组合条件评估：遍历 rules，按 AND/OR 逻辑组合
     */
    public boolean evaluateRules(List<?> rules, Requirement requirement, String logic) {
        if (rules == null || rules.isEmpty()) {
            return true;
        }
        if (requirement == null) {
            return false;
        }
        boolean isAnd = "AND".equalsIgnoreCase(logic);
        for (Object ruleObj : rules) {
            if (!(ruleObj instanceof Map<?, ?> ruleMap)) {
                continue;
            }
            boolean result = evaluateSingleRule(ruleMap, requirement);
            if (isAnd && !result) {
                return false; // AND: 一个 false 即全 false
            }
            if (!isAnd && result) {
                return true;  // OR: 一个 true 即全 true
            }
        }
        return isAnd; // AND: 全部通过 → true; OR: 全部不通过 → false
    }

    /**
     * 评估单条规则（结构化）
     */
    @SuppressWarnings("unchecked")
    private boolean evaluateSingleRule(Map<?, ?> ruleMap, Requirement requirement) {
        Object fieldObj = ruleMap.get("field");
        Object operatorObj = ruleMap.get("operator");
        Object valueObj = ruleMap.get("value");
        if (fieldObj == null || operatorObj == null) {
            return true;
        }
        String field = fieldObj.toString();
        String operator = operatorObj.toString();
        String actual = readRequirementField(requirement, field);

        return switch (operator.toLowerCase()) {
            case "eq" -> Objects.equals(normalize(actual), normalize(String.valueOf(valueObj)));
            case "ne" -> !Objects.equals(normalize(actual), normalize(String.valueOf(valueObj)));
            case "in" -> evaluateIn(actual, valueObj);
            case "notin" -> !evaluateIn(actual, valueObj);
            case "isempty" -> actual == null || actual.isBlank();
            case "notempty" -> actual != null && !actual.isBlank();
            case "matches" -> safeRegexMatch(actual, valueObj == null ? "" : String.valueOf(valueObj));
            default -> true;
        };
    }

    /**
     * 旧版兼容：简单表达式评估（仅支持 field == 'value' / field != 'value'）
     */
    private boolean evaluateSimpleExpr(String expr, Requirement requirement) {
        if (requirement == null) {
            return false;
        }
        Matcher matcher = SIMPLE_EXPR.matcher(expr);
        if (!matcher.matches()) {
            log.warn("条件表达式格式不支持，按放行处理: {}", expr);
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

    /**
     * 评估 in 运算符：实际值是否在指定列表中
     */
    private boolean evaluateIn(String actual, Object valueObj) {
        if (valueObj instanceof List<?> list) {
            return list.stream().map(v -> normalize(String.valueOf(v))).anyMatch(v -> Objects.equals(normalize(actual), v));
        }
        return Objects.equals(normalize(actual), normalize(String.valueOf(valueObj)));
    }

    /**
     * 安全正则匹配（带 ReDoS 防护）
     * - 正则长度限制 128 字符
     * - 限定字符集（禁止命名组等复杂特性）
     * - 需求字段值长度限制 1024
     * - 2 秒超时保护
     */
    private boolean safeRegexMatch(String actual, String regex) {
        if (actual == null || actual.isBlank()) {
            return false;
        }
        if (actual.length() > FIELD_VALUE_MAX_LENGTH) {
            log.warn("条件正则评估：需求字段值超长({})，截断处理", actual.length());
            actual = actual.substring(0, FIELD_VALUE_MAX_LENGTH);
        }
        if (regex.length() > REGEX_MAX_LENGTH) {
            log.warn("条件正则评估：正则表达式超长({}>{}), 跳过匹配: {}", regex.length(), REGEX_MAX_LENGTH, regex);
            return false;
        }
        if (!SAFE_REGEX_CHARSET.matcher(regex).matches()) {
            log.warn("条件正则评估：正则含不安全字符集，跳过匹配: {}", regex);
            return false;
        }
        final Pattern pattern;
        try {
            pattern = Pattern.compile(regex);
        } catch (Exception e) {
            log.warn("条件正则评估：正则编译失败: {}", regex);
            return false;
        }
        final String fieldValue = actual;
        Future<Boolean> future = REGEX_EXECUTOR.submit(() -> pattern.matcher(fieldValue).find());
        try {
            return future.get(REGEX_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("条件正则评估：匹配超时({}ms), 跳过: {}", REGEX_TIMEOUT_MS, regex);
            return false;
        } catch (Exception e) {
            log.warn("条件正则评估：匹配异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 统一从 edge.condition 或 edge.properties.condition 读取条件配置
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveCondition(WorkflowEdge edge) {
        if (edge == null) {
            return null;
        }
        // 优先读取 condition 字段
        if (edge.getCondition() != null && !edge.getCondition().isEmpty()) {
            return edge.getCondition();
        }
        // 兼容：从 properties.condition 读取
        if (edge.getProperties() != null) {
            Object condition = edge.getProperties().get("condition");
            if (condition instanceof Map<?, ?> conditionMap && !conditionMap.isEmpty()) {
                return (Map<String, Object>) conditionMap;
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

    /**
     * 保留旧版结构化评估接口（仅供并行分支等场景使用）
     */
    public boolean evaluateStructuredCondition(Object condition, Requirement requirement) {
        if (!(condition instanceof Map<?, ?> conditionMap)) {
            return true;
        }
        if (requirement == null) {
            return false;
        }
        return evaluateSingleRule((Map<?, ?>) condition, requirement);
    }
}
