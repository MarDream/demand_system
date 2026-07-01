package com.demand.system.module.requirement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.requirement.dto.RequirementCommentVO;
import com.demand.system.module.requirement.entity.RequirementComment;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface RequirementCommentMapper extends BaseMapper<RequirementComment> {

    /**
     * 根据需求ID查询评论列表
     * @param requirementId 需求ID
     * @return 评论VO列表
     */
    List<RequirementCommentVO> selectByRequirementId(@Param("requirementId") Long requirementId);
}
