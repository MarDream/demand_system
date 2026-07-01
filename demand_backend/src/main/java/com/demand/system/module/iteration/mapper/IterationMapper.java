package com.demand.system.module.iteration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.iteration.entity.Iteration;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

public interface IterationMapper extends BaseMapper<Iteration> {

    /**
     * 查询迭代详情，包含需求数量和完成数量
     *
     * @param id 迭代ID
     * @return 迭代详情信息
     */
    Map<String, Object> selectDetailById(@Param("id") Long id);

    /**
     * 统计指定迭代下的需求数量
     *
     * @param iterationId 迭代ID
     * @return 需求数量
     */
    int countRequirements(@Param("iterationId") Long iterationId);
}
