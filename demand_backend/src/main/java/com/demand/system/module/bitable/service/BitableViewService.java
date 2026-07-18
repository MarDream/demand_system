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

    /**
     * 复制视图（不复制记录）
     *
     * @param viewId 源视图ID
     * @param userId 操作者ID
     * @return 新视图的 ID
     */
    Long duplicateView(Long viewId, Long userId);

    /**
     * 获取单个视图详情
     *
     * @param viewId 视图ID
     * @return 视图详情
     */
    BitableViewVO getViewById(Long viewId);

    /**
     * 设置默认视图
     *
     * @param tableId 数据表ID
     * @param viewId  要设为默认的视图ID
     */
    void setDefaultView(Long tableId, Long viewId);
}