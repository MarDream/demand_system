package com.demand.system.module.workflow.dto;

import jakarta.validation.constraints.NotNull;

public class FlowTransitionRequest {

    @NotNull(message = "需求ID不能为空")
    private Long requirementId;

    @NotNull(message = "目标节点ID不能为空")
    private String toNodeId;

    private Long projectId;

    private String action;

    private String comment;

    /** 审批环节评价星级 1-5 */
    private Integer rating;

    private Integer lockVersion;

    public Long getRequirementId() {
        return requirementId;
    }

    public void setRequirementId(Long requirementId) {
        this.requirementId = requirementId;
    }

    public String getToNodeId() {
        return toNodeId;
    }

    public void setToNodeId(String toNodeId) {
        this.toNodeId = toNodeId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public Integer getLockVersion() {
        return lockVersion;
    }

    public void setLockVersion(Integer lockVersion) {
        this.lockVersion = lockVersion;
    }
}
