package com.demand.system.module.bitable.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.bitable.entity.BitableTableGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 多维表格-数据表分组 Mapper
 */
@Mapper
public interface BitableTableGroupMapper extends BaseMapper<BitableTableGroup> {

    /**
     * 查询指定 Base 下所有未删除的分组（扁平结构，树由 Service 组装）
     *
     * @param baseId 多维表格容器ID
     * @return 分组列表，按层级与排序号升序
     */
    List<BitableTableGroup> selectByBaseId(@Param("baseId") Long baseId);

    /**
     * 统计指定 Base 下的分组数量
     *
     * @param baseId 多维表格容器ID
     * @return 分组数量
     */
    int countByBaseId(@Param("baseId") Long baseId);

    /**
     * 查询指定分组的直接子分组ID列表（未删除）
     *
     * @param parentId 父分组ID
     * @return 子分组ID列表
     */
    List<Long> selectChildIds(@Param("parentId") Long parentId);

    /**
     * 计算某父级下已有分组的最大排序号，空则为 0
     *
     * @param baseId   多维表格容器ID
     * @param parentId 父分组ID，可为 null（根层级）
     * @return 最大排序号
     */
    int selectMaxSortOrder(@Param("baseId") Long baseId, @Param("parentId") Long parentId);
}
