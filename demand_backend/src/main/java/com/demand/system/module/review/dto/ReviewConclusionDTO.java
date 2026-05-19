package com.demand.system.module.review.dto;

public class ReviewConclusionDTO {

    private Integer totalReviews;

    private Integer passedCount;

    private Integer failedCount;

    private Integer needModificationCount;

    private String conclusion;

    private String conclusionDetail;

    public Integer getTotalReviews() {
        return totalReviews;
    }

    public void setTotalReviews(Integer totalReviews) {
        this.totalReviews = totalReviews;
    }

    public Integer getPassedCount() {
        return passedCount;
    }

    public void setPassedCount(Integer passedCount) {
        this.passedCount = passedCount;
    }

    public Integer getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(Integer failedCount) {
        this.failedCount = failedCount;
    }

    public Integer getNeedModificationCount() {
        return needModificationCount;
    }

    public void setNeedModificationCount(Integer needModificationCount) {
        this.needModificationCount = needModificationCount;
    }

    public String getConclusion() {
        return conclusion;
    }

    public void setConclusion(String conclusion) {
        this.conclusion = conclusion;
    }

    public String getConclusionDetail() {
        return conclusionDetail;
    }

    public void setConclusionDetail(String conclusionDetail) {
        this.conclusionDetail = conclusionDetail;
    }
}
