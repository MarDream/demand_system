package com.demand.system.module.iteration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.iteration.entity.Iteration;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

public interface IterationMapper extends BaseMapper<Iteration> {

    @Select("SELECT i.*, COUNT(r.id) as requirement_count, " +
            "SUM(CASE WHEN r.status IN ('已上线','已验收') THEN 1 ELSE 0 END) as completed_count " +
            "FROM iterations i LEFT JOIN requirements r ON i.id = r.iteration_id AND r.deleted_at = 0 " +
            "WHERE i.id = #{id} GROUP BY i.id")
    Map<String, Object> selectDetailById(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM requirements WHERE iteration_id = #{iterationId} AND deleted_at = 0")
    int countRequirements(@Param("iterationId") Long iterationId);
}
