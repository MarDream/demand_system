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
    private Long evaluatorId;
    private String evaluatorName;
    private Integer rating;
    private String content;
    private LocalDateTime createdAt;

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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
