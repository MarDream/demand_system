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
}
