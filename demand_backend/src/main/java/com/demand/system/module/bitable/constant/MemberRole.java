package com.demand.system.module.bitable.constant;

/**
 * 多维表格成员角色枚举
 */
public enum MemberRole {

    OWNER("owner", "所有者"),
    ADMIN("admin", "管理员"),
    EDITOR("editor", "编辑者"),
    COMMENTER("commenter", "评论者"),
    VIEWER("viewer", "只读");

    private final String code;
    private final String label;

    MemberRole(String code, String label) {
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
     * @param code 角色编码
     * @return 对应枚举，未找到返回 null
     */
    public static MemberRole fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (MemberRole role : values()) {
            if (role.code.equals(code)) {
                return role;
            }
        }
        return null;
    }
}
