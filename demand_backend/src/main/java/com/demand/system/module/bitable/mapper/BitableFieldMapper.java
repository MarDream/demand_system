package com.demand.system.module.bitable.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.bitable.entity.BitableField;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 多维表格-字段定义 Mapper
 */
@Mapper
public interface BitableFieldMapper extends BaseMapper<BitableField> {

    /**
     * 按数据表ID查询所有未删除的字段
     *
     * @param tableId 数据表ID
     * @return 字段定义列表
     */
    List<BitableField> selectByTableId(@Param("tableId") Long tableId);

    /**
     * 统计指定数据表下的字段数量
     *
     * @param tableId 数据表ID
     * @return 字段数量
     */
    int countByTableId(@Param("tableId") Long tableId);

    /**
     * 更新字段排序
     *
     * @param id        字段ID
     * @param sortOrder 排序值
     */
    void updateSortOrder(@Param("id") Long id, @Param("sortOrder") Integer sortOrder);

    /**
     * 查询指定数据表下字段的最大排序值
     *
     * @param tableId 数据表ID
     * @return 最大排序值，无字段时返回 null
     */
    Integer selectMaxSortOrderByTableId(@Param("tableId") Long tableId);

    /**
     * 批量更新字段排序（CASE WHEN 单条 SQL）
     *
     * @param list 包含 id 和 sortOrder 的映射列表
     */
    void batchUpdateSortOrder(@Param("list") java.util.List<java.util.Map<String, Object>> list);
}
