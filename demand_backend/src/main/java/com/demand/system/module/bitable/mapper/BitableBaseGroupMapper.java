package com.demand.system.module.bitable.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.bitable.entity.BitableBaseGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 多维表格-Base分组 Mapper
 */
@Mapper
public interface BitableBaseGroupMapper extends BaseMapper<BitableBaseGroup> {

    /**
     * 查询所有未删除的分组（扁平结构，树由 Service 组装）
     *
     * @return 分组列表，按层级与排序号升序
     */
    List<BitableBaseGroup> selectAll();

    /**
     * 计算某父级下已有分组的最大排序号，空则为 0
     *
     * @param parentId 父分组ID，可为 null（根层级）
     * @return 最大排序号
     */
    int selectMaxSortOrder(@Param("parentId") Long parentId);

    /**
     * 按传入顺序批量回写排序号（列表下标即 sort_order）
     *
     * @param ids 按目标顺序排列的分组ID列表
     * @return 受影响行数
     */
    int updateSortOrders(@Param("ids") List<Long> ids);

    /**
     * 同名检测：统计同一父级（含根层级）下同名的分组数量。
     *
     * @param parentId  父分组ID，NULL 表示根层级（SQL 用 NULL-safe 比较）
     * @param name      目标分组名
     * @param excludeId 重命名/移动时排除自身的分组ID，新建传 null
     * @return 同名分组数量
     */
    int countSameNameInParent(@Param("parentId") Long parentId,
                              @Param("name") String name,
                              @Param("excludeId") Long excludeId);
}
