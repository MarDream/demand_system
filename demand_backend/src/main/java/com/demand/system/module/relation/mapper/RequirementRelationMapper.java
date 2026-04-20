package com.demand.system.module.relation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.relation.entity.RequirementRelation;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface RequirementRelationMapper extends BaseMapper<RequirementRelation> {

    @Select("SELECT rr.*, r.title as target_title, r.status as target_status, r.priority as target_priority " +
            "FROM requirement_relations rr " +
            "LEFT JOIN requirements r ON rr.target_id = r.id " +
            "WHERE rr.source_id = #{sourceId}")
    List<Map<String, Object>> selectWithTarget(@Param("sourceId") Long sourceId);

    @Select("SELECT COUNT(*) FROM requirement_relations WHERE source_id = #{sourceId} AND target_id = #{targetId} AND relation_type = #{relationType}")
    int exists(@Param("sourceId") Long sourceId, @Param("targetId") Long targetId, @Param("relationType") String relationType);
}
