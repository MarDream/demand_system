package com.demand.system.module.bitable.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.bitable.entity.BitableRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 多维表格-记录行 Mapper
 */
@Mapper
public interface BitableRecordMapper extends BaseMapper<BitableRecord> {

    /**
     * 按数据表ID分页查询未删除的记录行
     *
     * @param tableId 数据表ID
     * @param offset  偏移量
     * @param limit   限制数量
     * @return 记录行列表
     */
    List<BitableRecord> selectByTableId(@Param("tableId") Long tableId, @Param("offset") Integer offset, @Param("limit") Integer limit);

    /**
     * 统计指定数据表下的记录行数量
     *
     * @param tableId 数据表ID
     * @return 记录行数量
     */
    int countByTableId(@Param("tableId") Long tableId);

    /**
     * 物理删除指定数据表下的所有记录行（用于删除表时级联）
     *
     * @param tableId 数据表ID
     * @return 删除行数
     */
    int deleteByTableId(@Param("tableId") Long tableId);
}
