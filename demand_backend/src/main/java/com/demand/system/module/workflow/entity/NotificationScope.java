package com.demand.system.module.workflow.entity;

/**
 * 工作流转通知作用域枚举。
 *
 * @see com.demand.system.module.workflow.service.WorkflowNotificationService
 */
public enum NotificationScope {

    /** 沿已审批节点路径上审批过需求的所有用户（含创建人） */
    PATH_APPROVERS,

    /** 从需求创建节点到当前节点实际处理过的所有用户 */
    ACTUAL_HANDLERS;

    /**
     * 从字符串值解析枚举，不匹配时返回默认值 {@link #PATH_APPROVERS}。
     *
     * @param value 字符串值（不区分大小写，自动 trim）
     * @return 对应的枚举值，未知值返回 PATH_APPROVERS
     */
    public static NotificationScope fromString(String value) {
        if (value == null || value.isBlank()) {
            return PATH_APPROVERS;
        }
        String normalized = value.trim().toUpperCase();
        for (NotificationScope scope : values()) {
            if (scope.name().equals(normalized)) {
                return scope;
            }
        }
        return PATH_APPROVERS;
    }
}
