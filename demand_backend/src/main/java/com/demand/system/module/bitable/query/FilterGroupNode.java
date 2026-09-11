package com.demand.system.module.bitable.query;

import java.util.List;
import java.util.Locale;

/** 逻辑筛选组。 */
public record FilterGroupNode(String logic, List<FilterNode> children) implements FilterNode {

    public FilterGroupNode {
        logic = "or".equalsIgnoreCase(logic) ? "or" : "and";
        children = List.copyOf(children == null ? List.of() : children);
    }

    public boolean isOr() {
        return "or".equals(logic.toLowerCase(Locale.ROOT));
    }
}