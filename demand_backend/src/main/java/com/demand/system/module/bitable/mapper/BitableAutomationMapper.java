package com.demand.system.module.bitable.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.bitable.entity.BitableAutomation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 多维表格-自动化规则 Mapper
 */
@Mapper
public interface BitableAutomationMapper extends BaseMapper<BitableAutomation> {

    /**
     * 按多维表格ID查询自动化规则列表
     *
     * @param baseId 多维表格ID
     * @return 自动化规则列表
     */
    List<BitableAutomation> selectByBaseId(@Param("baseId") Long baseId);

    /**
     * 按数据表ID和状态查询自动化规则列表
     *
     * @param tableId 数据表ID
     * @param status  状态
     * @return 自动化规则列表
     */
    List<BitableAutomation> selectByTableIdAndStatus(@Param("tableId") Long tableId, @Param("status") String status);

    /**
     * 停用指定 Base 下所有启用的自动化规则（删除 Base 时级联停用）
     *
     * @param baseId 多维表格ID
     * @return 影响行数
     */
    int disableByBaseId(@Param("baseId") Long baseId);
}
