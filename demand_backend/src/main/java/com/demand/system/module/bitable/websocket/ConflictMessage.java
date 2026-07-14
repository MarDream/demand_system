package com.demand.system.module.bitable.websocket;

/**
 * 冲突通知消息
 *
 * @param baseId    多维表格容器ID
 * @param tableId   数据表ID
 * @param recordId  记录ID
 * @param fieldId   字段ID
 * @param message   冲突提示信息
 */
public record ConflictMessage(
        Long baseId,
        Long tableId,
        Long recordId,
        Long fieldId,
        String message
) {
}
