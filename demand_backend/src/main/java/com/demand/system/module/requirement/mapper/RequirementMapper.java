package com.demand.system.module.requirement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.requirement.entity.Requirement;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

public interface RequirementMapper extends BaseMapper<Requirement> {

    @Select("SELECT r.*, u1.real_name as creator_name, u2.real_name as assignee_name, " +
            "(SELECT COUNT(*) FROM requirements WHERE parent_id = r.id AND deleted_at = 0) as child_count " +
            "FROM requirements r " +
            "LEFT JOIN users u1 ON r.creator_id = u1.id " +
            "LEFT JOIN users u2 ON r.assignee_id = u2.id " +
            "WHERE r.id = #{id} AND r.deleted_at = 0")
    Map<String, Object> selectDetailById(@Param("id") Long id);
}
