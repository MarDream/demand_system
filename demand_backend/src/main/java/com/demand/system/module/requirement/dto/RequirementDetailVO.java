package com.demand.system.module.requirement.dto;

import java.util.List;
import java.util.Map;

/**
 * 需求详情综合VO，包含需求基本信息及所有关联数据
 */
public class RequirementDetailVO {

    /**
     * 需求基本信息
     */
    private RequirementVO requirement;

    /**
     * 变更历史
     */
    private List<Map<String, Object>> history;

    /**
     * 子需求列表
     */
    private List<Map<String, Object>> children;

    /**
     * 关联需求列表
     */
    private List<Map<String, Object>> relations;

    /**
     * 评论列表
     */
    private List<RequirementCommentVO> comments;

    /**
     * 审批评价列表
     */
    private List<RequirementApprovalEvaluationVO> approvalEvaluations;

    public RequirementVO getRequirement() {
        return requirement;
    }

    public void setRequirement(RequirementVO requirement) {
        this.requirement = requirement;
    }

    public List<Map<String, Object>> getHistory() {
        return history;
    }

    public void setHistory(List<Map<String, Object>> history) {
        this.history = history;
    }

    public List<Map<String, Object>> getChildren() {
        return children;
    }

    public void setChildren(List<Map<String, Object>> children) {
        this.children = children;
    }

    public List<Map<String, Object>> getRelations() {
        return relations;
    }

    public void setRelations(List<Map<String, Object>> relations) {
        this.relations = relations;
    }

    public List<RequirementCommentVO> getComments() {
        return comments;
    }

    public void setComments(List<RequirementCommentVO> comments) {
        this.comments = comments;
    }

    public List<RequirementApprovalEvaluationVO> getApprovalEvaluations() {
        return approvalEvaluations;
    }

    public void setApprovalEvaluations(List<RequirementApprovalEvaluationVO> approvalEvaluations) {
        this.approvalEvaluations = approvalEvaluations;
    }
}
