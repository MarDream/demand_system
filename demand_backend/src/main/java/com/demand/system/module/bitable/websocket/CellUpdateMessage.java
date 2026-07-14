package com.demand.system.module.bitable.websocket;

/**
 * 单元格更新广播消息
 *
 * @param baseId    多维表格容器ID
 * @param tableId   数据表ID
 * @param recordId  记录ID
 * @param fieldId   字段ID
 * @param value     新值
 * @param updatedBy 修改人名称
 * @param userId    修改人用户ID
 * @param version   更新后的版本号
 */
public record CellUpdateMessage(
        Long baseId,
        Long tableId,
        Long recordId,
        Long fieldId,
        Object value,
        String updatedBy,
        Long userId,
        Integer version
) {
}
