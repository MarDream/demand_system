package com.demand.system.common.enums;

public enum IterationStatus {

    NOT_STARTED("未开始"),
    IN_PROGRESS("进行中"),
    COMPLETED("已完成"),
    CLOSED("已关闭");

    private final String label;

    IterationStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
