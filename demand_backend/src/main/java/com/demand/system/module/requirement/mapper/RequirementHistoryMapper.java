package com.demand.system.module.requirement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.requirement.entity.RequirementHistory;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface RequirementHistoryMapper extends BaseMapper<RequirementHistory> {

    /**
     * 查询需求历史记录
     *
     * @param requirementId 需求ID
     * @return 历史记录列表（包含操作人姓名）
     */
    List<Map<String, Object>> selectHistoryByRequirement(@Param("requirementId") Long requirementId);
}
