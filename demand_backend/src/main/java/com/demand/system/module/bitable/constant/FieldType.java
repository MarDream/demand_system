package com.demand.system.module.bitable.constant;

/**
 * 多维表格字段类型枚举
 */
public enum FieldType {

    TEXT("text", "文本"),
    NUMBER("number", "数字"),
    DATE("date", "日期"),
    DATE_RANGE("date_range", "日期范围"),
    SINGLE_SELECT("single_select", "单选"),
    MULTI_SELECT("multi_select", "多选"),
    USER("user", "人员"),
    GROUP("group", "群组"),
    DEPARTMENT("department", "部门"),
    CHECK("check", "勾选(兼容旧版)"),
    CHECKBOX("checkbox", "复选框"),
    PROCESS("process", "流程"),
    BUTTON("button", "按钮"),
    AUTO_NUMBER("auto_number", "自动编号"),
    CREATED_TIME("created_time", "创建时间"),
    MODIFIED_TIME("modified_time", "修改时间(兼容旧版)"),
    LAST_MODIFIED_TIME("last_modified_time", "最后更新时间"),
    CREATED_USER("created_user", "创建人(兼容旧版)"),
    MODIFIED_USER("modified_user", "修改人(兼容旧版)"),
    CREATED_BY("created_by", "创建人"),
    MODIFIED_BY("modified_by", "修改人"),
    URL("url", "链接"),
    EMAIL("email", "邮箱"),
    PHONE("phone", "电话"),
    LOCATION("location", "地理位置"),
    BARCODE("barcode", "条码"),
    CURRENCY("currency", "货币"),
    PROGRESS("progress", "进度"),
    RATING("rating", "评分"),
    LINK("link", "单向关联"),
    BIDIRECTIONAL_LINK("bidirectional_link", "双向关联"),
    ROLLUP("rollup", "汇总"),
    LOOKUP("lookup", "查找引用"),
    FORMULA("formula", "公式"),
    ATTACHMENT("attachment", "附件"),
    AI_TEXT("ai_text", "AI文本"),
    AI_SELECT("ai_select", "AI单选");

    private final String code;
    private final String label;

    FieldType(String code, String label) {
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
     * @param code 字段类型编码
     * @return 对应枚举，未找到返回 null
     */
    public static FieldType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (FieldType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 是否为选择类型(单选/多选)
     *
     * @return true=选择类型
     */
    public boolean isSelectType() {
        return this == SINGLE_SELECT || this == MULTI_SELECT;
    }

    /**
     * 是否为AI类型
     *
     * @return true=AI类型
     */
    public boolean isAiType() {
        return this == AI_TEXT || this == AI_SELECT;
    }

    /**
     * 是否为自动类型(系统自动填充)
     *
     * @return true=自动类型
     */
    public boolean isAutoType() {
        return this == CREATED_TIME || this == MODIFIED_TIME || this == LAST_MODIFIED_TIME
                || this == CREATED_USER || this == MODIFIED_USER || this == CREATED_BY || this == MODIFIED_BY
                || this == AUTO_NUMBER;
    }
}
