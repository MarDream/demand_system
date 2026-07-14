package com.demand.system.module.bitable.constant;

/**
 * 多维表格操作类型枚举
 */
public enum OperationType {

    INSERT_RECORD("insert_record", "新增记录"),
    UPDATE_CELL("update_cell", "更新单元格"),
    DELETE_RECORD("delete_record", "删除记录"),
    ADD_FIELD("add_field", "新增字段"),
    UPDATE_FIELD("update_field", "更新字段"),
    DELETE_FIELD("delete_field", "删除字段"),
    ADD_VIEW("add_view", "新增视图"),
    UPDATE_VIEW("update_view", "更新视图"),
    DELETE_VIEW("delete_view", "删除视图"),
    ADD_TABLE("add_table", "新增数据表"),
    UPDATE_TABLE("update_table", "更新数据表"),
    DELETE_TABLE("delete_table", "删除数据表"),
    UPDATE_BASE("update_base", "更新多维表格"),
    ADD_MEMBER("add_member", "新增成员"),
    REMOVE_MEMBER("remove_member", "移除成员");

    private final String code;
    private final String label;

    OperationType(String code, String label) {
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
     * @param code 操作类型编码
     * @return 对应枚举，未找到返回 null
     */
    public static OperationType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (OperationType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
