package com.demand.system.module.requirement.service;

import com.demand.system.module.requirement.dto.RequirementApprovalEvaluationVO;
import com.demand.system.module.requirement.dto.RequirementAttachmentDTO;
import com.demand.system.module.workflow.entity.WorkflowInstance;
import com.demand.system.module.workflow.entity.WorkflowNode;

import java.util.List;
import java.util.Map;

public interface RequirementApprovalEvaluationService {

    List<RequirementApprovalEvaluationVO> listByRequirementId(Long requirementId);

    void saveOnTransition(WorkflowInstance instance, WorkflowNode actionNode, Long transitionId,
                          Long evaluatorId, String content);

    void saveOnTransition(WorkflowInstance instance, WorkflowNode actionNode, Long transitionId,
                          Long evaluatorId, String content, List<RequirementAttachmentDTO> attachments);

    void saveOnApprovalTransition(WorkflowInstance instance, WorkflowNode approvalNode, Long transitionId,
                                  Long evaluatorId, Integer rating, String content);

    void saveOnApprovalTransition(WorkflowInstance instance, WorkflowNode approvalNode, Long transitionId,
                                  Long evaluatorId, Integer rating, String content, List<RequirementAttachmentDTO> attachments);

    void saveOnApprovalTransition(WorkflowInstance instance, WorkflowNode approvalNode, Long transitionId,
                                  Long evaluatorId, Integer rating, Map<String, Integer> ratingDimensions,
                                  String content, List<RequirementAttachmentDTO> attachments);

    void addSupplement(Long requirementId, Long parentEvaluationId, Long operatorId, String content);

    void addSupplement(Long requirementId, Long parentEvaluationId, Long operatorId, String content,
                       List<RequirementAttachmentDTO> attachments);

    /**
     * 校验评分值是否在有效范围内（1-5 星），允许 null
     */
    void validateRating(Integer rating);
}
