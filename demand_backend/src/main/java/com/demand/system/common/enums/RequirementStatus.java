package com.demand.system.common.enums;

import lombok.Getter;

@Getter
public enum RequirementStatus {

    NEW("新建"),
    PENDING_REVIEW("待评审"),
    REVIEWING("评审中"),
    APPROVED("已通过"),
    IN_DEVELOPMENT("开发中"),
    TESTING("测试中"),
    RELEASED("已上线"),
    ACCEPTED("已验收"),
    REJECTED("已拒绝"),
    CANCELLED("已取消"),
    SENT_BACK("打回"),
    TEST_FAILED("测试不通过"),
    ACCEPT_FAILED("验收不通过");

    private final String label;

    RequirementStatus(String label) {
        this.label = label;
    }
}
