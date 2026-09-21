package com.demand.system.module.bitable.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.bitable.entity.BitableTable;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 多维表格-数据表 Mapper
 */
@Mapper
public interface BitableTableMapper extends BaseMapper<BitableTable> {

    /**
     * 按 BaseId 查询所有未删除的数据表
     *
     * @param baseId 多维表格容器ID
     * @return 数据表列表
     */
    List<BitableTable> selectByBaseId(@Param("baseId") Long baseId);

    /**
     * 按传入顺序批量回写排序号（列表下标即 sort_order），仅限当前 Base 内
     *
     * @param baseId 多维表格容器ID
     * @param ids    按目标顺序排列的数据表ID列表
     * @return 受影响行数
     */
    int updateSortOrders(@Param("baseId") Long baseId, @Param("ids") List<Long> ids);

    /**
     * 统计指定 Base 下的数据表数量
     *
     * @param baseId 多维表格容器ID
     * @return 数据表数量
     */
    int countByBaseId(@Param("baseId") Long baseId);

    /**
     * 同名检测：统计同一「Base 分组展示范围」（含未分组）内同类型同名的数据表数量。
     * 数据表在目录树上按其 Base 的分组挂载，因此以 Base 的 group_id 为唯一性作用域。
     *
     * @param groupId   Base 所属分组ID，NULL 表示未分组（SQL 用 NULL-safe 比较）
     * @param name      目标表名
     * @param excludeId 重命名时排除自身的数据表ID，新建传 null
     * @return 同名数据表数量
     */
    int countSameNameInGroupScope(@Param("groupId") Long groupId,
                                  @Param("name") String name,
                                  @Param("excludeId") Long excludeId);

    /**
     * 同一目录树层级（= 同一 Base 分组，含未分组）下数据表的最大 sort_order。
     *
     * @param groupId Base 所属分组ID，NULL 表示未分组（SQL 用 NULL-safe 比较）
     * @return 最大排序号；该层级无数据表时返回 null
     */
    Integer selectMaxSortOrderInGroup(@Param("groupId") Long groupId);
}
