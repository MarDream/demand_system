package com.demand.system.module.bitable.websocket;

/**
 * 光标移动广播消息
 *
 * @param userId   用户ID
 * @param userName 用户名称
 * @param tableId  数据表ID
 * @param recordId 记录ID
 * @param fieldId  字段ID
 */
public record CursorMessage(
        Long userId,
        String userName,
        Long tableId,
        Long recordId,
        Long fieldId
) {
}
