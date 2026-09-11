package com.demand.system.module.git.dto;

import jakarta.validation.constraints.NotBlank;

public class MergeRequestDTO {

    @NotBlank(message = "源分支不能为空")
    private String sourceBranch;

    @NotBlank(message = "目标分支不能为空")
    private String targetBranch;

    @NotBlank(message = "标题不能为空")
    private String title;

    private String description;

    private String mergeStrategy;

    private String ciStatus;

    private Long requirementId;

    private String remoteMrId;

    public String getSourceBranch() {
        return sourceBranch;
    }

    public void setSourceBranch(String sourceBranch) {
        this.sourceBranch = sourceBranch;
    }

    public String getTargetBranch() {
        return targetBranch;
    }

    public void setTargetBranch(String targetBranch) {
        this.targetBranch = targetBranch;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMergeStrategy() {
        return mergeStrategy;
    }

    public void setMergeStrategy(String mergeStrategy) {
        this.mergeStrategy = mergeStrategy;
    }

    public String getCiStatus() {
        return ciStatus;
    }

    public void setCiStatus(String ciStatus) {
        this.ciStatus = ciStatus;
    }

    public Long getRequirementId() {
        return requirementId;
    }

    public void setRequirementId(Long requirementId) {
        this.requirementId = requirementId;
    }

    public String getRemoteMrId() {
        return remoteMrId;
    }

    public void setRemoteMrId(String remoteMrId) {
        this.remoteMrId = remoteMrId;
    }
}
