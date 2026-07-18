package com.demand.system.module.bitable.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.bitable.entity.BitableAutomationRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 多维表格-自动化执行记录 Mapper
 */
@Mapper
public interface BitableAutomationRunMapper extends BaseMapper<BitableAutomationRun> {

    /**
     * 按自动化规则ID分页查询执行记录
     *
     * @param automationId 自动化规则ID
     * @param offset       偏移量
     * @param limit        限制数量
     * @return 执行记录列表
     */
    List<BitableAutomationRun> selectByAutomationId(@Param("automationId") Long automationId,
                                                     @Param("offset") Integer offset,
                                                     @Param("limit") Integer limit);

    /**
     * 统计指定自动化规则的执行记录数量
     *
     * @param automationId 自动化规则ID
     * @return 执行记录数量
     */
    int countByAutomationId(@Param("automationId") Long automationId);

    /**
     * 查询指定自动化规则的最近一次执行记录
     *
     * @param automationId 自动化规则ID
     * @return 最近一次执行记录
     */
    BitableAutomationRun selectLatestByAutomationId(@Param("automationId") Long automationId);
}
