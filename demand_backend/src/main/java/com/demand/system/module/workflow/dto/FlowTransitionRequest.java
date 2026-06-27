package com.demand.system.module.workflow.dto;

import com.demand.system.module.requirement.dto.RequirementAttachmentDTO;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public class FlowTransitionRequest {

    @NotNull(message = "需求ID不能为空")
    private Long requirementId;

    @NotNull(message = "目标节点ID不能为空")
    private String toNodeId;

    private Long projectId;

    private String action;

    private String comment;

    /** 整体评分 1-5 */
    private Integer rating;

    /** 多维评分 key=维度标识 value=分数 */
    private Map<String, Integer> ratingDimensions;

    private Integer lockVersion;

    /** 审批附件 */
    private List<RequirementAttachmentDTO> attachments;

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

    public Map<String, Integer> getRatingDimensions() {
        return ratingDimensions;
    }

    public void setRatingDimensions(Map<String, Integer> ratingDimensions) {
        this.ratingDimensions = ratingDimensions;
    }

    public Integer getLockVersion() {
        return lockVersion;
    }

    public void setLockVersion(Integer lockVersion) {
        this.lockVersion = lockVersion;
    }

    public List<RequirementAttachmentDTO> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<RequirementAttachmentDTO> attachments) {
        this.attachments = attachments;
    }
}
