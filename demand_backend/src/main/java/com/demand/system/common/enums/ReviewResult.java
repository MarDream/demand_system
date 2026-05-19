package com.demand.system.common.enums;

public enum ReviewResult {

    PASSED("通过"),
    FAILED("不通过"),
    NEED_MODIFICATION("需修改");

    private final String label;

    ReviewResult(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
