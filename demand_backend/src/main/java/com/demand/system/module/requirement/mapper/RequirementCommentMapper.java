package com.demand.system.module.requirement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.requirement.dto.RequirementCommentVO;
import com.demand.system.module.requirement.entity.RequirementComment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface RequirementCommentMapper extends BaseMapper<RequirementComment> {

    @Select("SELECT rc.id, rc.requirement_id, rc.user_id, u.real_name AS user_name, rc.content, rc.created_at " +
            "FROM requirement_comments rc " +
            "LEFT JOIN users u ON rc.user_id = u.id " +
            "WHERE rc.requirement_id = #{requirementId} " +
            "ORDER BY rc.created_at DESC, rc.id DESC")
    List<RequirementCommentVO> selectByRequirementId(@Param("requirementId") Long requirementId);
}
