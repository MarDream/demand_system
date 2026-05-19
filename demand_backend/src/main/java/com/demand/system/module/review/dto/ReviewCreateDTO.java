package com.demand.system.module.review.dto;

import jakarta.validation.constraints.NotNull;

public class ReviewCreateDTO {

    @NotNull(message = "需求ID不能为空")
    private Long requirementId;

    @NotNull(message = "评审人ID不能为空")
    private Long reviewerId;

    public Long getRequirementId() {
        return requirementId;
    }

    public void setRequirementId(Long requirementId) {
        this.requirementId = requirementId;
    }

    public Long getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(Long reviewerId) {
        this.reviewerId = reviewerId;
    }
}
