package com.demand.system.module.bitable.service;

import com.demand.system.module.bitable.dto.BitableFieldCreateDTO;
import com.demand.system.module.bitable.dto.BitableFieldUpdateDTO;
import com.demand.system.module.bitable.dto.BitableFieldVO;

import java.util.List;

/**
 * 多维表格-字段定义 Service
 */
public interface BitableFieldService {

    /**
     * 列出数据表的所有字段
     *
     * @param tableId 数据表ID
     * @return 字段列表
     */
    List<BitableFieldVO> listFields(Long tableId);

    /**
     * 创建字段
     *
     * @param tableId 数据表ID
     * @param dto     创建参数
     * @return 新字段的 ID
     */
    Long createField(Long tableId, BitableFieldCreateDTO dto);

    /**
     * 更新字段
     *
     * @param id  字段ID
     * @param dto 更新参数
     */
    void updateField(Long id, BitableFieldUpdateDTO dto);

    /**
     * 删除字段（同时物理删除该字段对应的 cell_values）
     *
     * @param id 字段ID
     */
    void deleteField(Long id);

    /**
     * 排序字段
     *
     * @param tableId  数据表ID
     * @param fieldIds 按顺序排列的字段ID列表
     */
    void sortFields(Long tableId, List<Long> fieldIds);
}