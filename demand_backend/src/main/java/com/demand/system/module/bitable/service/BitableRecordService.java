package com.demand.system.module.bitable.service;

import com.demand.system.module.bitable.dto.BitableRecordCreateDTO;
import com.demand.system.module.bitable.dto.BitableRecordVO;
import com.demand.system.common.result.PageResult;

import java.util.List;

/**
 * 多维表格-记录行 Service
 */
public interface BitableRecordService {

    /**
     * 分页列出数据表的记录行（含单元格值）
     *
     * @param tableId  数据表ID
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    PageResult<BitableRecordVO> listRecords(Long tableId, Integer pageNum, Integer pageSize);

    /**
     * 获取单条记录详情（含单元格值）
     *
     * @param id 记录ID
     * @return 记录详情
     */
    BitableRecordVO getRecordById(Long id);

    /**
     * 创建记录行
     *
     * @param tableId 数据表ID
     * @param dto     创建参数（含 cells）
     * @param userId  创建者ID
     * @return 新记录的 ID
     */
    Long createRecord(Long tableId, BitableRecordCreateDTO dto, Long userId);

    /**
     * 整行更新记录（乐观锁）
     *
     * @param id     记录ID
     * @param dto    更新参数（含 cells）
     * @param userId 更新者ID
     */
    void updateRecord(Long id, BitableRecordCreateDTO dto, Long userId);

    /**
     * 删除记录（软删 record + 物理删 cell_values + 软删 comments）
     *
     * @param id 记录ID
     */
    void deleteRecord(Long id);

    /**
     * 批量创建记录行
     *
     * @param tableId 数据表ID
     * @param dtos    创建参数列表
     * @param userId  创建者ID
     * @return 新记录的 ID 列表
     */
    Long batchCreateRecords(Long tableId, List<BitableRecordCreateDTO> dtos, Long userId);

    /**
     * 更新单个单元格值（乐观锁）
     *
     * @param recordId 记录ID
     * @param fieldId  字段ID
     * @param value    新值（CellValueDTO）
     * @param version  乐观锁版本号
     * @param userId   操作人ID
     * @return 更新后的版本号
     */
    Integer updateCell(Long recordId, Long fieldId, Object value, Integer version, Long userId);
}