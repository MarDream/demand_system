package com.demand.system.module.requirement.service.impl;

import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.util.UserNameResolver;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.rbac.entity.Role;
import com.demand.system.module.rbac.mapper.RoleMapper;
import com.demand.system.module.requirement.dto.RequirementApprovalEvaluationVO;
import com.demand.system.module.requirement.dto.RequirementAttachmentDTO;
import com.demand.system.module.requirement.entity.RequirementApprovalEvaluation;
import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.requirement.mapper.RequirementApprovalEvaluationMapper;
import com.demand.system.module.requirement.mapper.RequirementMapper;
import com.demand.system.module.requirement.service.RequirementApprovalEvaluationService;
import com.demand.system.module.requirement.service.RatingFeedbackService;
import com.demand.system.module.workflow.entity.WorkflowInstance;
import com.demand.system.module.workflow.entity.WorkflowInstanceTransition;
import com.demand.system.module.workflow.entity.WorkflowNode;
import com.demand.system.module.workflow.mapper.WorkflowInstanceMapper;
import com.demand.system.module.workflow.mapper.WorkflowInstanceTransitionMapper;
import com.demand.system.module.workflow.mapper.WorkflowNodeMapper;
import com.demand.system.module.workflow.support.WorkflowNodeUtils;
import org.springframework.beans.BeanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class RequirementApprovalEvaluationServiceImpl implements RequirementApprovalEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(RequirementApprovalEvaluationServiceImpl.class);

    private final RequirementApprovalEvaluationMapper evaluationMapper;
    private final WorkflowInstanceTransitionMapper transitionMapper;
    private final RequirementMapper requirementMapper;
    private final RatingFeedbackService feedbackService;
    private final UserNameResolver userNameResolver;
    private final WorkflowInstanceMapper instanceMapper;
    private final WorkflowNodeMapper nodeMapper;
    private final RoleMapper roleMapper;

    public RequirementApprovalEvaluationServiceImpl(RequirementApprovalEvaluationMapper evaluationMapper,
                                                   WorkflowInstanceTransitionMapper transitionMapper,
                                                   RequirementMapper requirementMapper,
                                                   RatingFeedbackService feedbackService,
                                                   UserNameResolver userNameResolver,
                                                   WorkflowInstanceMapper instanceMapper,
                                                   WorkflowNodeMapper nodeMapper,
                                                   RoleMapper roleMapper) {
        this.evaluationMapper = evaluationMapper;
        this.transitionMapper = transitionMapper;
        this.requirementMapper = requirementMapper;
        this.feedbackService = feedbackService;
        this.userNameResolver = userNameResolver;
        this.instanceMapper = instanceMapper;
        this.nodeMapper = nodeMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    public List<RequirementApprovalEvaluationVO> listByRequirementId(Long requirementId) {
        List<WorkflowInstanceTransition> transitions = transitionMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WorkflowInstanceTransition>()
                        .eq(WorkflowInstanceTransition::getRequirementId, requirementId)
                        .orderByAsc(WorkflowInstanceTransition::getCreatedAt)
                        .orderByAsc(WorkflowInstanceTransition::getId));
        List<RequirementApprovalEvaluation> evaluations = evaluationMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RequirementApprovalEvaluation>()
                        .eq(RequirementApprovalEvaluation::getRequirementId, requirementId)
                        .orderByAsc(RequirementApprovalEvaluation::getCreatedAt)
                        .orderByAsc(RequirementApprovalEvaluation::getId));

        Map<Long, RequirementApprovalEvaluation> evaluationByTransitionId = new LinkedHashMap<>();
        Map<Long, List<RequirementApprovalEvaluation>> supplementsByParentId = new LinkedHashMap<>();
        for (RequirementApprovalEvaluation evaluation : evaluations) {
            if (Boolean.TRUE.equals(evaluation.getIsSupplement()) && evaluation.getParentId() != null) {
                supplementsByParentId.computeIfAbsent(evaluation.getParentId(), key -> new ArrayList<>()).add(evaluation);
                continue;
            }
            if (evaluation.getTransitionId() != null) {
                evaluationByTransitionId.put(evaluation.getTransitionId(), evaluation);
            }
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();
        Requirement requirement = resolveRequirement(requirementId);
        Map<Long, String> transitionRoleNames = resolveTransitionAssigneeRoleNames(transitions);
        Map<Long, RequirementApprovalEvaluationVO> topLevelByRecordId = new LinkedHashMap<>();
        List<RequirementApprovalEvaluationVO> records = new ArrayList<>();
        for (WorkflowInstanceTransition transition : transitions) {
            RequirementApprovalEvaluation evaluation = evaluationByTransitionId.get(transition.getId());
            String action = normalizeAction(transition);
            String comment = resolveComment(transition, evaluation);
            if (evaluation == null && !StringUtils.hasText(comment) && !shouldKeepTransitionRecord(action)) {
                continue;
            }

            RequirementApprovalEvaluationVO vo = buildTopLevelRecord(transition, evaluation, action, comment, currentUserId, requirement);
            // 角色展示优先用流转时快照的操作人角色，缺失时回退到来源节点配置的处理角色
            String operatorRole = transition.getOperatorRoleName();
            vo.setAssigneeRoleName(StringUtils.hasText(operatorRole) ? operatorRole : transitionRoleNames.get(transition.getId()));
            records.add(vo);
            if (evaluation != null) {
                topLevelByRecordId.put(evaluation.getId(), vo);
            }
        }

        for (Map.Entry<Long, List<RequirementApprovalEvaluation>> entry : supplementsByParentId.entrySet()) {
            RequirementApprovalEvaluationVO parent = topLevelByRecordId.get(entry.getKey());
            if (parent == null) {
                continue;
            }
            List<RequirementApprovalEvaluationVO> supplements = new ArrayList<>();
            for (RequirementApprovalEvaluation supplement : entry.getValue()) {
                supplements.add(buildSupplementRecord(supplement));
            }
            parent.setSupplements(supplements);
        }
        return records;
    }

    @Override
    public void saveOnTransition(WorkflowInstance instance, WorkflowNode actionNode, Long transitionId,
                                 Long evaluatorId, String content) {
        saveTransitionRecord(instance, actionNode, transitionId, evaluatorId, null, content, null);
    }

    @Override
    public void saveOnTransition(WorkflowInstance instance, WorkflowNode actionNode, Long transitionId,
                                 Long evaluatorId, String content, List<RequirementAttachmentDTO> attachments) {
        saveTransitionRecord(instance, actionNode, transitionId, evaluatorId, null, content, attachments);
    }

    @Override
    public void saveOnApprovalTransition(WorkflowInstance instance, WorkflowNode approvalNode, Long transitionId,
                                         Long evaluatorId, Integer rating, String content) {
        validateRating(rating);
        saveTransitionRecord(instance, approvalNode, transitionId, evaluatorId, rating, null, content, null);
    }

    @Override
    public void saveOnApprovalTransition(WorkflowInstance instance, WorkflowNode approvalNode, Long transitionId,
                                         Long evaluatorId, Integer rating, String content, List<RequirementAttachmentDTO> attachments) {
        validateRating(rating);
        saveTransitionRecord(instance, approvalNode, transitionId, evaluatorId, rating, null, content, attachments);
    }

    @Override
    public void saveOnApprovalTransition(WorkflowInstance instance, WorkflowNode approvalNode, Long transitionId,
                                         Long evaluatorId, Integer rating, Map<String, Integer> ratingDimensions,
                                         String content, List<RequirementAttachmentDTO> attachments) {
        validateRating(rating);
        if (rating == null && ratingDimensions != null) {
            int sum = 0;
            int count = 0;
            for (Integer v : ratingDimensions.values()) {
                if (v != null) {
                    if (v < 1 || v > 5) {
                        throw new BusinessException(400, "评分必须在 1-5 星之间");
                    }
                    sum += v;
                    count++;
                }
            }
            if (count > 0) {
                rating = (int) Math.round((double) sum / count);
            }
        }
        saveTransitionRecord(instance, approvalNode, transitionId, evaluatorId, rating, ratingDimensions, content, attachments);
    }

    @Override
    public void addSupplement(Long requirementId, Long parentEvaluationId, Long operatorId, String content) {
        addSupplement(requirementId, parentEvaluationId, operatorId, content, null);
    }

    @Override
    public void addSupplement(Long requirementId, Long parentEvaluationId, Long operatorId, String content,
                              List<RequirementAttachmentDTO> attachments) {
        Requirement requirement = requirementMapper.selectById(requirementId);
        if (requirement == null) {
            throw new BusinessException(404, "需求不存在");
        }
        RequirementApprovalEvaluation parent = evaluationMapper.selectById(parentEvaluationId);
        if (parent == null || !Objects.equals(parent.getRequirementId(), requirementId)) {
            throw new BusinessException(404, "原审核记录不存在");
        }
        if (Boolean.TRUE.equals(parent.getIsSupplement())) {
            throw new BusinessException(400, "补充意见不能继续补充");
        }
        if (!canSupplement(requirement, operatorId)) {
            throw new BusinessException(403, "您没有权限补充该审核意见");
        }

        RequirementApprovalEvaluation supplement = new RequirementApprovalEvaluation();
        supplement.setRequirementId(requirementId);
        supplement.setInstanceId(parent.getInstanceId());
        supplement.setTransitionId(parent.getTransitionId());
        supplement.setNodeId(parent.getNodeId());
        supplement.setNodeName(parent.getNodeName());
        supplement.setNodeStatusCode(parent.getNodeStatusCode());
        supplement.setParentId(parent.getId());
        supplement.setIsSupplement(true);
        supplement.setEvaluatorId(operatorId);
        supplement.setRating(null);
        supplement.setContent(normalizeContent(content));
        supplement.setAttachments(attachments);
        evaluationMapper.insert(supplement);
    }

    @Override
    public void validateRating(Integer rating) {
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new BusinessException(400, "评分必须在 1-5 星之间");
        }
    }

    private String normalizeContent(String content) {
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(400, "补充意见不能为空");
        }
        return trimToMaxLength(content);
    }

    private String normalizeOptionalContent(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        return trimToMaxLength(content);
    }

    private String trimToMaxLength(String content) {
        String trimmed = content.trim();
        return trimmed.length() > 1000 ? trimmed.substring(0, 1000) : trimmed;
    }

    private void saveTransitionRecord(WorkflowInstance instance, WorkflowNode actionNode, Long transitionId,
                                      Long evaluatorId, Integer rating, String content, List<RequirementAttachmentDTO> attachments) {
        saveTransitionRecord(instance, actionNode, transitionId, evaluatorId, rating, null, content, attachments);
    }

    private void saveTransitionRecord(WorkflowInstance instance, WorkflowNode actionNode, Long transitionId,
                                      Long evaluatorId, Integer rating, Map<String, Integer> ratingDimensions,
                                      String content, List<RequirementAttachmentDTO> attachments) {
        if (instance == null || transitionId == null || evaluatorId == null) {
            return;
        }
        RequirementApprovalEvaluation existing = evaluationMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RequirementApprovalEvaluation>()
                        .eq(RequirementApprovalEvaluation::getTransitionId, transitionId)
                        .eq(RequirementApprovalEvaluation::getIsSupplement, false)
                        .last("LIMIT 1"));
        if (existing != null) {
            return;
        }
        WorkflowInstanceTransition transition = transitionMapper.selectById(transitionId);
        if (transition == null) {
            return;
        }

        RequirementApprovalEvaluation evaluation = new RequirementApprovalEvaluation();
        evaluation.setRequirementId(instance.getRequirementId());
        evaluation.setInstanceId(instance.getId());
        evaluation.setTransitionId(transitionId);
        evaluation.setNodeId(resolveStoredNodeId(transition, actionNode));
        evaluation.setNodeName(resolveStoredNodeName(transition, actionNode));
        evaluation.setNodeStatusCode(actionNode != null ? WorkflowNodeUtils.resolveNodeStatusCode(actionNode, false) : null);
        evaluation.setParentId(null);
        evaluation.setIsSupplement(false);
        evaluation.setEvaluatorId(evaluatorId);
        evaluation.setRating(rating);
        evaluation.setRatingDimensions(ratingDimensions);
        evaluation.setContent(normalizeOptionalContent(content));
        evaluation.setAttachments(attachments);
        evaluationMapper.insert(evaluation);

        // 反馈回路：低分告警
        try {
            feedbackService.onEvaluationCreated(evaluation);
        } catch (Exception ex) {
            log.warn("评分反馈触发失败，不影响主流程: {}", ex.getMessage());
        }
    }

    /**
     * 批量解析流转记录的操作节点处理角色名（记录ID → 角色名）。
     * 链路：transition.instanceId → 实例.workflowVersionId + fromNodeId → 节点.assigneeRoleId → 角色名。
     * 节点未按角色指派（指定人/创建者等）或任一环节缺失时返回 null，前端仅展示处理人姓名。
     */
    private Map<Long, String> resolveTransitionAssigneeRoleNames(List<WorkflowInstanceTransition> transitions) {
        Map<Long, String> result = new LinkedHashMap<>();
        if (transitions == null || transitions.isEmpty()) {
            return result;
        }

        // 1) 实例：拿到每个流转所属的工作流版本
        java.util.Set<Long> instanceIds = new java.util.HashSet<>();
        for (WorkflowInstanceTransition t : transitions) {
            if (t.getInstanceId() != null) {
                instanceIds.add(t.getInstanceId());
            }
        }
        if (instanceIds.isEmpty()) {
            return result;
        }
        Map<Long, Long> instanceVersionMap = new LinkedHashMap<>();
        for (WorkflowInstance instance : instanceMapper.selectBatchIds(instanceIds)) {
            if (instance != null && instance.getWorkflowVersionId() != null) {
                instanceVersionMap.put(instance.getId(), instance.getWorkflowVersionId());
            }
        }

        // 2) 节点：版本 + fromNodeId 定位配置的处理角色
        java.util.Set<Long> versionIds = new java.util.HashSet<>(instanceVersionMap.values());
        java.util.Set<String> nodeIds = new java.util.HashSet<>();
        for (WorkflowInstanceTransition t : transitions) {
            if (StringUtils.hasText(t.getFromNodeId())) {
                nodeIds.add(t.getFromNodeId());
            }
        }
        Map<String, WorkflowNode> nodeByKey = new LinkedHashMap<>();
        if (!versionIds.isEmpty() && !nodeIds.isEmpty()) {
            for (WorkflowNode node : nodeMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WorkflowNode>()
                            .in(WorkflowNode::getWorkflowVersionId, versionIds)
                            .in(WorkflowNode::getNodeId, nodeIds))) {
                if (node != null) {
                    nodeByKey.put(node.getWorkflowVersionId() + ":" + node.getNodeId(), node);
                }
            }
        }

        // 3) 角色：assigneeRoleId → 角色名
        java.util.Set<Long> roleIds = new java.util.HashSet<>();
        for (WorkflowNode node : nodeByKey.values()) {
            if (node.getAssigneeRoleId() != null) {
                roleIds.add(node.getAssigneeRoleId().longValue());
            }
        }
        Map<Long, String> roleNameMap = new LinkedHashMap<>();
        if (!roleIds.isEmpty()) {
            for (Role role : roleMapper.selectBatchIds(roleIds)) {
                if (role != null) {
                    roleNameMap.put(role.getId(), role.getName());
                }
            }
        }

        for (WorkflowInstanceTransition t : transitions) {
            Long versionId = instanceVersionMap.get(t.getInstanceId());
            WorkflowNode node = (versionId != null && StringUtils.hasText(t.getFromNodeId()))
                    ? nodeByKey.get(versionId + ":" + t.getFromNodeId()) : null;
            if (node == null || node.getAssigneeRoleId() == null) {
                continue;
            }
            String roleName = roleNameMap.get(node.getAssigneeRoleId().longValue());
            if (StringUtils.hasText(roleName)) {
                result.put(t.getId(), roleName);
            }
        }
        return result;
    }

    private RequirementApprovalEvaluationVO buildTopLevelRecord(WorkflowInstanceTransition transition,
                                                                RequirementApprovalEvaluation evaluation,
                                                                String action,
                                                                String comment,
                                                                Long currentUserId,
                                                                Requirement requirement) {
        RequirementApprovalEvaluationVO vo = new RequirementApprovalEvaluationVO();
        vo.setId(evaluation != null ? evaluation.getId() : transition.getId());
        vo.setRequirementId(transition.getRequirementId());
        vo.setInstanceId(transition.getInstanceId());
        vo.setTransitionId(transition.getId());
        vo.setNodeId(resolveNodeId(transition));
        vo.setNodeName(resolveNodeName(transition, action, evaluation));
        vo.setFromNodeName(transition != null ? transition.getFromNodeName() : null);
        vo.setToNodeName(transition != null ? transition.getToNodeName() : null);
        vo.setNodeStatusCode(evaluation != null ? evaluation.getNodeStatusCode() : null);
        vo.setParentId(null);
        vo.setIsSupplement(false);
        vo.setCanSupplement(evaluation != null && canSupplement(requirement, currentUserId));
        vo.setEvaluatorId(transition.getOperatorId());
        vo.setEvaluatorName(userNameResolver.resolveUserName(transition.getOperatorId()));
        vo.setEvaluatorUsername(userNameResolver.resolveUserAccount(transition.getOperatorId()));
        vo.setAction(action);
        vo.setActionLabel(resolveActionLabel(transition, action));
        vo.setResult(resolveResult(transition, action));
        vo.setResultLabel(resolveResultLabel(transition, action));
        vo.setRating(evaluation != null ? evaluation.getRating() : null);
        vo.setRatingDimensions(evaluation != null ? evaluation.getRatingDimensions() : null);
        vo.setContent(comment);
        vo.setAttachments(evaluation != null ? evaluation.getAttachments() : null);
        vo.setCreatedAt(transition.getCreatedAt());
        vo.setSupplements(new ArrayList<>());
        return vo;
    }

    private RequirementApprovalEvaluationVO buildSupplementRecord(RequirementApprovalEvaluation supplement) {
        RequirementApprovalEvaluationVO vo = new RequirementApprovalEvaluationVO();
        BeanUtils.copyProperties(supplement, vo);
        vo.setCanSupplement(false);
        vo.setEvaluatorName(userNameResolver.resolveUserName(supplement.getEvaluatorId()));
        vo.setEvaluatorUsername(userNameResolver.resolveUserAccount(supplement.getEvaluatorId()));
        vo.setAction("supplement");
        vo.setActionLabel("补充意见");
        vo.setResult("SUPPLEMENT");
        vo.setResultLabel("补充");
        vo.setSupplements(List.of());
        return vo;
    }

    private Requirement resolveRequirement(Long requirementId) {
        return requirementId == null ? null : requirementMapper.selectById(requirementId);
    }

    private boolean canSupplement(Requirement requirement, Long operatorId) {
        if (requirement == null || operatorId == null) {
            return false;
        }
        if (SecurityUtils.hasAnyRole("admin", "super_admin", "SUPER_ADMIN")) {
            return true;
        }
        if (Objects.equals(requirement.getCreatorId(), operatorId)) {
            return true;
        }
        Long participated = transitionMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WorkflowInstanceTransition>()
                        .eq(WorkflowInstanceTransition::getRequirementId, requirement.getId())
                        .eq(WorkflowInstanceTransition::getOperatorId, operatorId));
        return participated != null && participated > 0;
    }

    private String resolveStoredNodeId(WorkflowInstanceTransition transition, WorkflowNode node) {
        if (transition != null && "submit".equals(normalizeAction(transition)) && isStartNode(transition)) {
            return transition.getToNodeId();
        }
        if (node != null && StringUtils.hasText(node.getNodeId())) {
            return node.getNodeId();
        }
        return transition != null ? transition.getFromNodeId() : null;
    }

    private String resolveStoredNodeName(WorkflowInstanceTransition transition, WorkflowNode node) {
        if (transition != null && "submit".equals(normalizeAction(transition)) && isStartNode(transition)) {
            return "提交";
        }
        if (node != null && StringUtils.hasText(node.getNodeName())) {
            return node.getNodeName();
        }
        if (transition != null && StringUtils.hasText(transition.getFromNodeName())) {
            return transition.getFromNodeName();
        }
        return transition != null ? transition.getToNodeName() : null;
    }

    private String normalizeAction(WorkflowInstanceTransition transition) {
        if (transition == null || !StringUtils.hasText(transition.getAction())) {
            return "submit";
        }
        return transition.getAction().trim().toLowerCase();
    }

    private boolean shouldKeepTransitionRecord(String action) {
        return "submit".equals(action) || "rollback".equals(action) || "cancel".equals(action)
                || "draft".equals(action);
    }

    private String resolveComment(WorkflowInstanceTransition transition, RequirementApprovalEvaluation evaluation) {
        if (evaluation != null && StringUtils.hasText(evaluation.getContent())) {
            return evaluation.getContent();
        }
        return normalizeOptionalContent(transition != null ? transition.getComment() : null);
    }

    private String resolveNodeId(WorkflowInstanceTransition transition) {
        if (transition == null) {
            return null;
        }
        if ("submit".equals(normalizeAction(transition)) && isStartNode(transition)) {
            return transition.getToNodeId();
        }
        return transition.getFromNodeId();
    }

    private String resolveNodeName(WorkflowInstanceTransition transition, String action, RequirementApprovalEvaluation evaluation) {
        if (transition == null) {
            return null;
        }
        // 审核记录应显示操作发生的节点（来源节点），而不是流转到的目标节点
        if ("submit".equals(action) && isStartNode(transition)) {
            return "新建";
        }
        if (StringUtils.hasText(transition.getFromNodeName())) {
            return transition.getFromNodeName();
        }
        // 降级：如果没有来源节点，则使用evaluation或目标节点
        if (evaluation != null && StringUtils.hasText(evaluation.getNodeName())) {
            return evaluation.getNodeName();
        }
        return transition.getToNodeName();
    }

    private boolean isStartNode(WorkflowInstanceTransition transition) {
        return transition != null && "start".equalsIgnoreCase(transition.getFromNodeId());
    }


    private String resolveActionLabel(WorkflowInstanceTransition transition, String action) {
        return switch (action) {
            case "rollback" -> "驳回";
            case "cancel" -> "取消";
            case "draft" -> "创建草稿";
            case "submit" -> isStartNode(transition) ? "提交" : "提交审核";
            default -> "审核";
        };
    }

    private String resolveResult(WorkflowInstanceTransition transition, String action) {
        return switch (action) {
            case "rollback" -> "REJECT";
            case "cancel" -> "CANCEL";
            case "draft" -> "DRAFT";
            case "submit" -> isStartNode(transition) ? "SUBMIT" : "PASS";
            default -> "PASS";
        };
    }

    private String resolveResultLabel(WorkflowInstanceTransition transition, String action) {
        return switch (action) {
            case "rollback" -> "驳回";
            case "cancel" -> "取消";
            case "draft" -> "草稿";
            case "submit" -> isStartNode(transition) ? "提交" : "通过";
            default -> "通过";
        };
    }
}
