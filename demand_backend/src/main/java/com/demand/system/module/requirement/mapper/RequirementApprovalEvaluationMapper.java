package com.demand.system.module.requirement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.requirement.dto.RequirementApprovalEvaluationVO;
import com.demand.system.module.requirement.entity.RequirementApprovalEvaluation;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface RequirementApprovalEvaluationMapper extends BaseMapper<RequirementApprovalEvaluation> {

    @Select("""
            SELECT e.id, e.requirement_id, e.instance_id, e.transition_id, e.node_id, e.node_name,
                   e.node_status_code, e.evaluator_id,
                   COALESCE(u.real_name, u.username) AS evaluator_name,
                   e.rating, e.content, e.created_at,
                   ns.name AS node_status_name
            FROM requirement_approval_evaluations e
            LEFT JOIN users u ON e.evaluator_id = u.id
            LEFT JOIN node_statuses ns ON e.node_status_code = ns.code
            WHERE e.requirement_id = #{requirementId}
            ORDER BY e.created_at ASC, e.id ASC
            """)
    List<RequirementApprovalEvaluationVO> selectByRequirementId(@Param("requirementId") Long requirementId);
}
