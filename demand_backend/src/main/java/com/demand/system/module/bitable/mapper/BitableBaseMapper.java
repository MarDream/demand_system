package com.demand.system.module.bitable.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.bitable.entity.BitableBase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 多维表格容器 Mapper
 */
@Mapper
public interface BitableBaseMapper extends BaseMapper<BitableBase> {

    /**
     * 查询某用户创建的所有多维表格容器
     *
     * @param userId 用户ID
     * @return 多维表格容器列表
     */
    List<BitableBase> selectByCreator(@Param("userId") Long userId);

    /**
     * 查询某用户是成员的所有多维表格容器
     *
     * @param userId 用户ID
     * @return 多维表格容器列表
     */
    List<BitableBase> selectByMember(@Param("userId") Long userId);

    /**
     * 查询多维表格容器详情，包含表数量聚合
     *
     * @param id 容器ID
     * @return 多维表格容器详情
     */
    BitableBase selectDetailById(@Param("id") Long id);

    /**
     * 按传入顺序批量回写排序号（列表下标即 sort_order）
     *
     * @param ids 按目标顺序排列的 Base ID 列表
     * @return 受影响行数
     */
    int updateSortOrders(@Param("ids") List<Long> ids);

    /**
     * 同名检测：统计同一分组（含未分组）下同名的多维表格数量。
     *
     * @param groupId   所属分组ID，NULL 表示未分组（SQL 用 NULL-safe 比较）
     * @param name      目标名称
     * @param excludeId 重命名/移动时排除自身的 Base ID，新建传 null
     * @return 同名多维表格数量
     */
    int countSameNameInGroup(@Param("groupId") Long groupId,
                             @Param("name") String name,
                             @Param("excludeId") Long excludeId);
}
