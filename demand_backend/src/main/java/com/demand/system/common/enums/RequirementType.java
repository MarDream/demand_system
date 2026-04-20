package com.demand.system.common.enums;

import lombok.Getter;

@Getter
public enum RequirementType {

    FEATURE("功能"),
    OPTIMIZATION("优化"),
    BUG("Bug"),
    TECH_DEBT("技术债务"),
    OPERATION("运营");

    private final String label;

    RequirementType(String label) {
        this.label = label;
    }
}
