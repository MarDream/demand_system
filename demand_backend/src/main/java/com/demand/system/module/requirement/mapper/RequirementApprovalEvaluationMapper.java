package com.demand.system.module.requirement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.requirement.dto.RequirementApprovalEvaluationVO;
import com.demand.system.module.requirement.entity.RequirementApprovalEvaluation;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface RequirementApprovalEvaluationMapper extends BaseMapper<RequirementApprovalEvaluation> {

    /**
     * 根据需求ID查询评估记录
     *
     * @param requirementId 需求ID
     * @return 评估记录列表
     */
    List<RequirementApprovalEvaluationVO> selectByRequirementId(@Param("requirementId") Long requirementId);
}
