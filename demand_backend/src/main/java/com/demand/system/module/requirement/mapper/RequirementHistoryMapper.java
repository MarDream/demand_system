package com.demand.system.module.requirement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.requirement.entity.RequirementHistory;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface RequirementHistoryMapper extends BaseMapper<RequirementHistory> {

    @Select("SELECT rh.*, u.real_name as operator_name FROM requirement_history rh LEFT JOIN users u ON rh.operator_id = u.id WHERE rh.requirement_id = #{requirementId} ORDER BY rh.created_at DESC")
    List<Map<String, Object>> selectHistoryByRequirement(@Param("requirementId") Long requirementId);
}
