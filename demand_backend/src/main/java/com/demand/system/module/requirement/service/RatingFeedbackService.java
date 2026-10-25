package com.demand.system.module.requirement.service;

import com.demand.system.module.requirement.entity.RequirementApprovalEvaluation;

public interface RatingFeedbackService {

    /**
     * 评分后触发反馈：低分告警、推送通知
     */
    void onEvaluationCreated(RequirementApprovalEvaluation evaluation);

    /**
     * 检测低分阈值（默认 3 星）
     */
    boolean isLowRating(Integer rating);
}
