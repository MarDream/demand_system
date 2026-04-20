package com.demand.system.module.review.dto;

import lombok.Data;

@Data
public class ReviewConclusionDTO {

    private Integer totalReviews;

    private Integer passedCount;

    private Integer failedCount;

    private Integer needModificationCount;

    private String conclusion;

    private String conclusionDetail;
}
