package com.demand.system.common.enums;

import lombok.Getter;

@Getter
public enum RelationType {

    PARENT_CHILD("父子"),
    DEPENDENCY("依赖"),
    BLOCKING("阻塞"),
    RELATED("相关"),
    DEFECT("关联缺陷"),
    TEST_CASE("关联用例");

    private final String label;

    RelationType(String label) {
        this.label = label;
    }
}
