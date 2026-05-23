package com.demand.system.module.requirement.service.impl;

import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.requirement.dto.RequirementApprovalEvaluationVO;
import com.demand.system.module.requirement.entity.RequirementApprovalEvaluation;
import com.demand.system.module.requirement.mapper.RequirementApprovalEvaluationMapper;
import com.demand.system.module.requirement.service.RequirementApprovalEvaluationService;
import com.demand.system.module.workflow.entity.WorkflowInstance;
import com.demand.system.module.workflow.entity.WorkflowNode;
import com.demand.system.module.workflow.support.WorkflowNodeUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class RequirementApprovalEvaluationServiceImpl implements RequirementApprovalEvaluationService {

    private final RequirementApprovalEvaluationMapper evaluationMapper;

    public RequirementApprovalEvaluationServiceImpl(RequirementApprovalEvaluationMapper evaluationMapper) {
        this.evaluationMapper = evaluationMapper;
    }

    @Override
    public List<RequirementApprovalEvaluationVO> listByRequirementId(Long requirementId) {
        return evaluationMapper.selectByRequirementId(requirementId);
    }

    @Override
    public void saveOnApprovalTransition(WorkflowInstance instance, WorkflowNode approvalNode, Long transitionId,
                                         Long evaluatorId, Integer rating, String content) {
        validateRating(rating);
        RequirementApprovalEvaluation evaluation = new RequirementApprovalEvaluation();
        evaluation.setRequirementId(instance.getRequirementId());
        evaluation.setInstanceId(instance.getId());
        evaluation.setTransitionId(transitionId);
        evaluation.setNodeId(approvalNode.getNodeId());
        evaluation.setNodeName(approvalNode.getNodeName());
        evaluation.setNodeStatusCode(WorkflowNodeUtils.resolveNodeStatusCode(approvalNode, false));
        evaluation.setEvaluatorId(evaluatorId);
        evaluation.setRating(rating);
        evaluation.setContent(normalizeContent(content));
        evaluationMapper.insert(evaluation);
    }

    private void validateRating(Integer rating) {
        if (rating == null || rating < 1 || rating > 5) {
            throw new BusinessException(400, "请选择 1-5 星审批评价");
        }
    }

    private String normalizeContent(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        String trimmed = content.trim();
        return trimmed.length() > 1000 ? trimmed.substring(0, 1000) : trimmed;
    }
}
