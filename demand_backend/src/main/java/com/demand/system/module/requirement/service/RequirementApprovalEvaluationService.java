package com.demand.system.module.requirement.service;

import com.demand.system.module.requirement.dto.RequirementApprovalEvaluationVO;
import com.demand.system.module.workflow.entity.WorkflowInstance;
import com.demand.system.module.workflow.entity.WorkflowNode;

import java.util.List;

public interface RequirementApprovalEvaluationService {

    List<RequirementApprovalEvaluationVO> listByRequirementId(Long requirementId);

    void saveOnApprovalTransition(WorkflowInstance instance, WorkflowNode approvalNode, Long transitionId,
                                  Long evaluatorId, Integer rating, String content);
}
