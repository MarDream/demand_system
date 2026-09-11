package com.demand.system.module.bitable.query;

import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 筛选配置解析器。
 *
 * <p>兼容历史数组格式与新的嵌套逻辑格式，并统一输出递归规则树：</p>
 * <ul>
 *   <li>数组：[{fieldId, operator, value, conjunction}, ...]</li>
 *   <li>嵌套组：{logic: "and"/"or", rules: [...]}</li>
 * </ul>
 */
public final class FilterTreeParser {

    /** 对外允许的筛选操作符白名单。 */
    public static final Set<String> SUPPORTED_OPERATORS = Set.of(
            "is_empty", "is_not_empty",
            "eq", "equals", "ne", "not_equals",
            "contains", "not_contains",
            "gt", "gte", "lt", "lte", "between");

    /** 单个筛选值的最大长度（字符串）。 */
    private static final int MAX_VALUE_LENGTH = 512;

    /** 嵌套逻辑组的最大深度，防止深递归耗尽栈/CPU。 */
    private static final int MAX_DEPTH = 8;

    private FilterTreeParser() {
    }

    /**
     * 解析筛选配置。
     *
     * @param filterConfig 筛选配置；null 表示没有筛选
     * @return 规则树；没有有效规则时返回 null
     */
    public static FilterNode parse(Object filterConfig) {
        if (filterConfig == null) {
            return null;
        }
        if (filterConfig instanceof List<?> list) {
            return parseArray(list, 0);
        }
        if (filterConfig instanceof Map<?, ?> map) {
            return parseGroup(map, 0);
        }
        throw validation("filterConfig 格式非法，仅支持数组或 {logic,rules}");
    }

    /** 将历史数组格式按左结合方式转换为逻辑树。 */
    private static FilterNode parseArray(List<?> items, int depth) {
        FilterNode accumulated = null;
        for (Object item : items) {
            ParsedArrayItem parsed = parseArrayItem(item, depth);
            if (parsed == null) {
                continue;
            }
            if (accumulated == null) {
                accumulated = parsed.node();
            } else {
                accumulated = new FilterGroupNode(
                        parsed.isOr() ? "or" : "and",
                        List.of(accumulated, parsed.node()));
            }
        }
        return accumulated;
    }

    private static ParsedArrayItem parseArrayItem(Object item, int depth) {
        if (!(item instanceof Map<?, ?> map)) {
            throw validation("筛选规则必须是对象");
        }
        FilterPredicateNode predicate = parsePredicate(map, depth);
        String conjunction = asString(map.get("conjunction"));
        return new ParsedArrayItem(predicate, "or".equalsIgnoreCase(conjunction));
    }

    /** 解析嵌套逻辑组，子项可以继续是组或叶子谓词。 */
    private static FilterNode parseGroup(Map<?, ?> map, int depth) {
        if (depth >= MAX_DEPTH) {
            throw validation("filterConfig 嵌套层级过深");
        }
        String logic = asString(map.get("logic"));
        if (logic == null) {
            logic = "and";
        }
        logic = logic.toLowerCase(Locale.ROOT);
        if (!"and".equals(logic) && !"or".equals(logic)) {
            throw validation("filterConfig.logic 仅支持 and/or");
        }

        Object rulesValue = map.get("rules");
        if (!(rulesValue instanceof List<?> rules)) {
            throw validation("filterConfig 嵌套格式缺少 rules 数组");
        }

        List<FilterNode> children = new ArrayList<>();
        for (Object item : rules) {
            if (!(item instanceof Map<?, ?> childMap)) {
                throw validation("筛选规则必须是对象");
            }
            if (childMap.containsKey("fieldId") || childMap.containsKey("operator")) {
                children.add(parsePredicate(childMap, depth));
            } else {
                FilterNode child = parseGroup(childMap, depth + 1);
                if (child != null) {
                    children.add(child);
                }
            }
        }
        if (children.isEmpty()) {
            throw validation("filterConfig 规则列表不能为空");
        }
        return children.size() == 1 ? children.get(0) : new FilterGroupNode(logic, children);
    }

    private static FilterPredicateNode parsePredicate(Map<?, ?> map, int depth) {
        Long fieldId = asLong(map.get("fieldId"));
        String operator = asString(map.get("operator"));
        if (fieldId == null || fieldId <= 0 || operator == null || operator.isBlank()) {
            throw validation("筛选规则缺少有效的 fieldId 或 operator");
        }

        String normalizedOperator = operator.toLowerCase(Locale.ROOT);
        if (!SUPPORTED_OPERATORS.contains(normalizedOperator)) {
            throw validation("不支持的筛选操作符");
        }

        Object value = map.get("value");
        if ("between".equals(normalizedOperator)) {
            validateBetween(value, depth);
        } else if (value instanceof String text && text.length() > MAX_VALUE_LENGTH) {
            throw validation("筛选值过长");
        }
        return new FilterPredicateNode(fieldId, normalizedOperator, value);
    }

    private static void validateBetween(Object value, int depth) {
        Object min;
        Object max;
        if (value instanceof List<?> list) {
            if (list.size() != 2) {
                throw validation("between 值必须是 [min, max]");
            }
            min = list.get(0);
            max = list.get(1);
        } else if (value instanceof Map<?, ?> map) {
            if (!map.containsKey("min") || !map.containsKey("max")) {
                throw validation("between 值必须包含 min 和 max");
            }
            min = map.get("min");
            max = map.get("max");
        } else {
            throw validation("between 值必须是 [min, max] 或 {min, max}");
        }
        if (min == null || max == null) {
            throw validation("between 的 min 和 max 不能为空");
        }
        if (min instanceof String text && text.length() > MAX_VALUE_LENGTH) {
            throw validation("between 最小值过长");
        }
        if (max instanceof String text && text.length() > MAX_VALUE_LENGTH) {
            throw validation("between 最大值过长");
        }

        BigDecimal minNumber = toBigDecimal(min);
        BigDecimal maxNumber = toBigDecimal(max);
        if (minNumber != null && maxNumber != null && minNumber.compareTo(maxNumber) > 0) {
            throw validation("between 的最小值不能大于最大值");
        }
    }

    private static Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.valueOf(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return new BigDecimal(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static BusinessException validation(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }

    private record ParsedArrayItem(FilterPredicateNode node, boolean isOr) {
    }
}