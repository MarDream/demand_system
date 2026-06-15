package com.demand.system.module.requirement.dto;

import java.time.LocalDateTime;

public class RequirementApprovalEvaluationVO {

    private Long id;
    private Long requirementId;
    private Long instanceId;
    private Long transitionId;
    private String nodeId;
    private String nodeName;
    private String nodeStatusCode;
    private String nodeStatusName;
    private Long parentId;
    private Boolean isSupplement;
    private Boolean canSupplement;
    private Long evaluatorId;
    private String evaluatorName;
    private String action;
    private String actionLabel;
    private String result;
    private String resultLabel;
    private Integer rating;
    private String content;
    private java.util.List<RequirementAttachmentDTO> attachments;
    private LocalDateTime createdAt;
    private java.util.List<RequirementApprovalEvaluationVO> supplements;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRequirementId() {
        return requirementId;
    }

    public void setRequirementId(Long requirementId) {
        this.requirementId = requirementId;
    }

    public Long getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(Long instanceId) {
        this.instanceId = instanceId;
    }

    public Long getTransitionId() {
        return transitionId;
    }

    public void setTransitionId(Long transitionId) {
        this.transitionId = transitionId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getNodeStatusCode() {
        return nodeStatusCode;
    }

    public void setNodeStatusCode(String nodeStatusCode) {
        this.nodeStatusCode = nodeStatusCode;
    }

    public String getNodeStatusName() {
        return nodeStatusName;
    }

    public void setNodeStatusName(String nodeStatusName) {
        this.nodeStatusName = nodeStatusName;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public Boolean getIsSupplement() {
        return isSupplement;
    }

    public void setIsSupplement(Boolean isSupplement) {
        this.isSupplement = isSupplement;
    }

    public Boolean getCanSupplement() {
        return canSupplement;
    }

    public void setCanSupplement(Boolean canSupplement) {
        this.canSupplement = canSupplement;
    }

    public Long getEvaluatorId() {
        return evaluatorId;
    }

    public void setEvaluatorId(Long evaluatorId) {
        this.evaluatorId = evaluatorId;
    }

    public String getEvaluatorName() {
        return evaluatorName;
    }

    public void setEvaluatorName(String evaluatorName) {
        this.evaluatorName = evaluatorName;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getActionLabel() {
        return actionLabel;
    }

    public void setActionLabel(String actionLabel) {
        this.actionLabel = actionLabel;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getResultLabel() {
        return resultLabel;
    }

    public void setResultLabel(String resultLabel) {
        this.resultLabel = resultLabel;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public java.util.List<RequirementAttachmentDTO> getAttachments() {
        return attachments;
    }

    public void setAttachments(java.util.List<RequirementAttachmentDTO> attachments) {
        this.attachments = attachments;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public java.util.List<RequirementApprovalEvaluationVO> getSupplements() {
        return supplements;
    }

    public void setSupplements(java.util.List<RequirementApprovalEvaluationVO> supplements) {
        this.supplements = supplements;
    }
}
