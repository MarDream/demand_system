package com.demand.system.common.enums;

import lombok.Getter;

@Getter
public enum Priority {

    P0_URGENT("P0-紧急"),
    P1_HIGH("P1-高"),
    P2_MEDIUM("P2-中"),
    P3_LOW("P3-低");

    private final String label;

    Priority(String label) {
        this.label = label;
    }
}
