package com.demand.system.module.bitable.service;

import com.demand.system.common.result.PageResult;
import com.demand.system.module.bitable.dto.BitableOperationVO;

/**
 * 多维表格操作历史 Service
 */
public interface BitableOperationService {

    /**
     * 记录操作历史
     *
     * @param baseId        多维表格容器ID
     * @param tableId       数据表ID
     * @param userId        操作人ID
     * @param operationType 操作类型（使用 OperationType 枚举的 code）
     * @param detail        操作详情（JSON 字符串）
     */
    void recordOperation(Long baseId, Long tableId, Long userId, String operationType, String detail);

    /**
     * 分页查询多维表格容器的操作历史
     *
     * @param baseId   多维表格容器ID
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    PageResult<BitableOperationVO> listOperationsByBaseId(Long baseId, Integer pageNum, Integer pageSize);

    /**
     * 分页查询数据表的操作历史
     *
     * @param baseId   多维表格容器ID
     * @param tableId  数据表ID
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    PageResult<BitableOperationVO> listOperationsByTableId(Long baseId, Long tableId, Integer pageNum, Integer pageSize);
}