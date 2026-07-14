package com.demand.system.module.bitable.service;

/**
 * 多维表格协作 Service
 */
public interface BitableCollaborationService {

    /**
     * 处理单元格更新：更新 DB 记录、记录操作日志、广播到 Redis
     *
     * @param baseId   多维表格容器ID
     * @param tableId  数据表ID
     * @param recordId 记录ID
     * @param fieldId  字段ID
     * @param value    新值
     * @param version  乐观锁版本号
     * @param userId   操作人ID
     */
    void handleCellUpdate(Long baseId, Long tableId, Long recordId, Long fieldId, Object value, Integer version, Long userId);
}
