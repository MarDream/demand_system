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
     * 统计指定 Base 下的数据表数量
     *
     * @param baseId 多维表格容器ID
     * @return 数据表数量
     */
    int countByBaseId(@Param("baseId") Long baseId);
}
