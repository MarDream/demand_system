package com.demand.system.module.requirement.service;

import com.demand.system.module.requirement.dto.RequirementApprovalEvaluationVO;
import com.demand.system.module.workflow.entity.WorkflowInstance;
import com.demand.system.module.workflow.entity.WorkflowNode;

import java.util.List;

public interface RequirementApprovalEvaluationService {

    List<RequirementApprovalEvaluationVO> listByRequirementId(Long requirementId);

    void saveOnTransition(WorkflowInstance instance, WorkflowNode actionNode, Long transitionId,
                          Long evaluatorId, String content);

    void saveOnApprovalTransition(WorkflowInstance instance, WorkflowNode approvalNode, Long transitionId,
                                  Long evaluatorId, Integer rating, String content);

    void addSupplement(Long requirementId, Long parentEvaluationId, Long operatorId, String content);
}
