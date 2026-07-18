package com.demand.system.module.bitable.constant;

/**
 * 多维表格视图类型枚举
 */
public enum ViewType {

    GRID("grid", "表格"),
    KANBAN("kanban", "看板"),
    GANTT("gantt", "甘特"),
    CALENDAR("calendar", "日历"),
    GALLERY("gallery", "画廊"),
    FORM("form", "表单");

    private final String code;
    private final String label;

    ViewType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 根据 code 查找枚举
     *
     * @param code 视图类型编码
     * @return 对应枚举，未找到返回 null
     */
    public static ViewType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ViewType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
