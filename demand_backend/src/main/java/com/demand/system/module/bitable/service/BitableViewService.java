package com.demand.system.module.bitable.service;

import com.demand.system.module.bitable.dto.BitableViewCreateDTO;
import com.demand.system.module.bitable.dto.BitableViewUpdateDTO;
import com.demand.system.module.bitable.dto.BitableViewVO;

import java.util.List;

/**
 * 多维表格-视图定义 Service
 */
public interface BitableViewService {

    /**
     * 列出数据表的所有视图
     *
     * @param tableId 数据表ID
     * @return 视图列表
     */
    List<BitableViewVO> listViews(Long tableId);

    /**
     * 创建视图
     *
     * @param tableId 数据表ID
     * @param dto     创建参数
     * @param userId  创建者ID
     * @return 新视图的 ID
     */
    Long createView(Long tableId, BitableViewCreateDTO dto, Long userId);

    /**
     * 更新视图
     *
     * @param id  视图ID
     * @param dto 更新参数
     */
    void updateView(Long id, BitableViewUpdateDTO dto);

    /**
     * 删除视图
     *
     * @param id 视图ID
     */
    void deleteView(Long id);
}