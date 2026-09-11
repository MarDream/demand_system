package com.demand.system.module.bitable.query;

/** 叶子筛选谓词。 */
public record FilterPredicateNode(Long fieldId, String operator, Object value) implements FilterNode {

    public FilterPredicateNode {
        operator = operator == null ? null : operator.toLowerCase(java.util.Locale.ROOT);
    }
}