package com.demand.system.module.bitable.service;

/**
 * 多维表格协作 Service
 */
public interface BitableCollaborationService {

    /**
     * 广播单元格更新：记录操作日志 + 通过 Redis 广播给协作端，不再写库。
     * <p>
     * DB 写入统一由 REST {@code BitableRecordService.updateCell} 完成，
     * 本方法仅负责"写库成功后"的协作广播，避免 REST 与 WS 双写同一条记录的 version 竞态。
     *
     * @param baseId     多维表格容器ID
     * @param tableId    数据表ID
     * @param recordId   记录ID
     * @param fieldId     字段ID
     * @param value       新值
     * @param newVersion  写库后的最新版本号（用于广播）
     * @param userId      操作人ID
     */
    void handleCellUpdate(Long baseId, Long tableId, Long recordId, Long fieldId, Object value, Integer newVersion, Long userId);

    /**
     * 广播记录创建/删除事件给协作端（仅广播，不写审计日志）。
     * 让其他在线客户端实时感知行的增删，而不必手动刷新。
     *
     * @param baseId     多维表格容器ID
     * @param tableId    数据表ID
     * @param recordId   记录ID
     * @param changeType record_created / record_deleted
     * @param userId     操作人ID
     */
    void broadcastRecordChanged(Long baseId, Long tableId, Long recordId, String changeType, Long userId);
}
