package com.demand.system.module.bitable.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.bitable.entity.BitableView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 多维表格-视图定义 Mapper
 */
@Mapper
public interface BitableViewMapper extends BaseMapper<BitableView> {

    /**
     * 按数据表ID查询所有未删除的视图
     *
     * @param tableId 数据表ID
     * @return 视图定义列表
     */
    List<BitableView> selectByTableId(@Param("tableId") Long tableId);

    /**
     * 统计指定数据表下的视图数量
     *
     * @param tableId 数据表ID
     * @return 视图数量
     */
    int countByTableId(@Param("tableId") Long tableId);
}
