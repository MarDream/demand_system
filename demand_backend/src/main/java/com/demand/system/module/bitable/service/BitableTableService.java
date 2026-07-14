package com.demand.system.module.bitable.service;

import com.demand.system.module.bitable.dto.BitableTableCreateDTO;
import com.demand.system.module.bitable.dto.BitableTableUpdateDTO;
import com.demand.system.module.bitable.dto.BitableTableVO;

import java.util.List;

/**
 * 多维表格-数据表 Service
 */
public interface BitableTableService {

    /**
     * 列出 Base 下的所有数据表
     *
     * @param baseId Base ID
     * @return 数据表列表
     */
    List<BitableTableVO> listTables(Long baseId);

    /**
     * 获取数据表详情
     *
     * @param id 数据表ID
     * @return 数据表详情
     */
    BitableTableVO getTableById(Long id);

    /**
     * 创建数据表，同时自动创建一个默认 grid 视图
     *
     * @param baseId Base ID
     * @param dto    创建参数
     * @param userId 创建者ID
     * @return 新数据表的 ID
     */
    Long createTable(Long baseId, BitableTableCreateDTO dto, Long userId);

    /**
     * 更新数据表
     *
     * @param id  数据表ID
     * @param dto 更新参数
     */
    void updateTable(Long id, BitableTableUpdateDTO dto);

    /**
     * 删除数据表（级联删除字段/记录/单元格/视图/评论）
     *
     * @param id 数据表ID
     */
    void deleteTable(Long id);
}