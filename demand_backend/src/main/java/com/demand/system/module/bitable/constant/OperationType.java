package com.demand.system.module.bitable.constant;

/**
 * 多维表格操作类型枚举
 */
public enum OperationType {

    INSERT_RECORD("insert_record", "新增记录"),
    UPDATE_RECORD("update_record", "更新记录"),
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
    ADD_TABLE_GROUP("add_table_group", "新增数据表分组"),
    UPDATE_TABLE_GROUP("update_table_group", "重命名数据表分组"),
    DELETE_TABLE_GROUP("delete_table_group", "删除数据表分组"),
    MOVE_TABLE_GROUP("move_table_group", "移动数据表分组"),
    MOVE_TABLE_TO_GROUP("move_table_to_group", "数据表归组"),
    CREATE_BASE("create_base", "创建多维表格"),
    UPDATE_BASE("update_base", "更新多维表格"),
    IMPORT_RECORDS("import_records", "导入记录"),
    EXPORT_RECORDS("export_records", "导出记录"),
    SHARE_VIEW("share_view", "开启视图分享"),
    UNSHARE_VIEW("unshare_view", "关闭视图分享"),
    ADD_MEMBER("add_member", "新增成员"),
    UPDATE_MEMBER_ROLE("update_member_role", "变更成员角色"),
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
