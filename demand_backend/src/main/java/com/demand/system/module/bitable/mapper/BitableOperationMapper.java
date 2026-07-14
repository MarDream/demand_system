package com.demand.system.module.bitable.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.bitable.entity.BitableOperation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 多维表格-操作历史审计 Mapper
 */
@Mapper
public interface BitableOperationMapper extends BaseMapper<BitableOperation> {

    /**
     * 分页查询指定多维表格容器的操作历史
     *
     * @param baseId 多维表格容器ID
     * @param offset 偏移量
     * @param limit  限制数量
     * @return 操作历史列表
     */
    List<BitableOperation> selectByBaseId(@Param("baseId") Long baseId, @Param("offset") Integer offset, @Param("limit") Integer limit);

    /**
     * 统计指定多维表格容器的操作历史数量
     *
     * @param baseId 多维表格容器ID
     * @return 操作历史数量
     */
    int countByBaseId(@Param("baseId") Long baseId);

    /**
     * 分页查询指定数据表的操作历史
     *
     * @param baseId 多维表格容器ID
     * @param tableId 数据表ID
     * @param offset  偏移量
     * @param limit   限制数量
     * @return 操作历史列表
     */
    List<BitableOperation> selectByTableId(@Param("baseId") Long baseId, @Param("tableId") Long tableId, @Param("offset") Integer offset, @Param("limit") Integer limit);

    /**
     * 统计指定数据表的操作历史数量
     *
     * @param baseId 多维表格容器ID
     * @param tableId 数据表ID
     * @return 操作历史数量
     */
    int countByTableId(@Param("baseId") Long baseId, @Param("tableId") Long tableId);
}
