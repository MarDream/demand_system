package com.demand.system.module.requirement.dto;

import com.demand.system.module.requirement.dto.RequirementAttachmentDTO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 低分需求 VO
 * 用于评分统计中展示需要改进的需求
 */
public class LowRatingRequirementVO {

    /**
     * 需求ID
     */
    private Long requirementId;

    /**
     * 需求编号
     */
    private String requirementNo;

    /**
     * 需求标题
     */
    private String title;

    /**
     * 节点名称（在哪个节点被评低分）
     */
    private String nodeName;

    /**
     * 节点ID
     */
    private String nodeId;

    /**
     * 整体评分（1-5星）
     */
    private Integer rating;

    /**
     * 多维评分详情
     * key: 维度标识(如 quality, response_speed)
     * value: 该维度的评分(1-5)
     */
    private Map<String, Integer> ratingDimensions;

    /**
     * 评价内容
     */
    private String comment;

    /**
     * 评价人ID
     */
    private Long evaluatorId;

    /**
     * 评价人姓名
     */
    private String evaluatorName;

    /**
     * 评价时间
     */
    private LocalDateTime createdAt;

    /**
     * 附件列表（如果有）
     */
    private List<RequirementAttachmentDTO> attachments;

    // Getters and Setters

    public Long getRequirementId() {
        return requirementId;
    }

    public void setRequirementId(Long requirementId) {
        this.requirementId = requirementId;
    }

    public String getRequirementNo() {
        return requirementNo;
    }

    public void setRequirementNo(String requirementNo) {
        this.requirementNo = requirementNo;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
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

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<RequirementAttachmentDTO> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<RequirementAttachmentDTO> attachments) {
        this.attachments = attachments;
    }
}
