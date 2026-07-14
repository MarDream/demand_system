package com.demand.system.module.bitable.websocket;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 客户端 → 服务端 WebSocket 消息封装
 *
 * @param type      消息类型：cell_update / cursor_move
 * @param baseId    多维表格容器ID
 * @param tableId   数据表ID
 * @param recordId  记录ID
 * @param fieldId   字段ID
 * @param value     新值（cell_update 时有效）
 * @param version   客户端持有的版本号（cell_update 时有效）
 * @param payload   原始扩展字段，用于后续协议扩展
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BitableWebSocketRequest(
        String type,
        Long baseId,
        Long tableId,
        Long recordId,
        Long fieldId,
        Object value,
        Integer version,
        JsonNode payload
) {
}
