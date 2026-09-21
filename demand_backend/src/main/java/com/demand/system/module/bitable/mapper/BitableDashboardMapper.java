package com.demand.system.module.bitable.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.bitable.entity.BitableDashboard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 多维表格-仪表盘 Mapper
 */
@Mapper
public interface BitableDashboardMapper extends BaseMapper<BitableDashboard> {

    /**
     * 同名检测：统计同一「Base 分组展示范围」（含未分组）内同类型同名的仪表盘数量。
     * 仪表盘在目录树上按其 Base 的分组挂载，因此以 Base 的 group_id 为唯一性作用域。
     *
     * @param groupId   Base 所属分组ID，NULL 表示未分组（SQL 用 NULL-safe 比较）
     * @param name      目标仪表盘名
     * @param excludeId 重命名时排除自身的仪表盘ID，新建传 null
     * @return 同名仪表盘数量
     */
    int countSameNameInGroupScope(@Param("groupId") Long groupId,
                                  @Param("name") String name,
                                  @Param("excludeId") Long excludeId);

    /**
     * 同一目录树层级（= 同一 Base 分组，含未分组）下仪表盘的最大 sort_order。
     *
     * @param groupId Base 所属分组ID，NULL 表示未分组（SQL 用 NULL-safe 比较）
     * @return 最大排序号；该层级无仪表盘时返回 null
     */
    Integer selectMaxSortOrderInGroup(@Param("groupId") Long groupId);
}
