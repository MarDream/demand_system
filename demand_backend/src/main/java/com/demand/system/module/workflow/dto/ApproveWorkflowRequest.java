package com.demand.system.module.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

/**
 * 审批工作流版本请求
 */
@Schema(description = "审批工作流版本请求")
public class ApproveWorkflowRequest {

    @NotNull(message = "版本ID不能为空")
    @Schema(description = "工作流版本ID")
    private Long versionId;

    @NotNull(message = "审批结果不能为空")
    @Schema(description = "是否通过（true=通过，false=驳回）")
    private Boolean approved;

    @Schema(description = "审批意见")
    private String comment;

    public Long getVersionId() {
        return versionId;
    }

    public void setVersionId(Long versionId) {
        this.versionId = versionId;
    }

    public Boolean getApproved() {
        return approved;
    }

    public void setApproved(Boolean approved) {
        this.approved = approved;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
