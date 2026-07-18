package com.demand.system.module.bitable.constant;

/**
 * 公式错误类型枚举
 * 对应电子表格中的标准错误标记
 */
public enum FormulaErrorType {

    ERROR("#ERROR!"),
    CIRCULAR_REF("#CIRC!"),
    DIV_ZERO("#DIV/0!"),
    TYPE_ERROR("#TYPE!"),
    REF_ERROR("#REF!"),
    NAME_ERROR("#NAME?");

    private final String display;

    FormulaErrorType(String display) {
        this.display = display;
    }

    public String getDisplay() {
        return display;
    }
}
