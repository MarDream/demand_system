package com.demand.system.module.assistant.dto;

public class AssistantSource {

    private String code;
    private String title;
    private String path;
    private String reason;
    /** 知识库文档 ID（知识库检索来源时有效） */
    private Long documentId;
    /** 知识库 ID（知识库检索来源时有效） */
    private Long knowledgeBaseId;
    /** 来源类型，例如 knowledge_document、requirement_body */
    private String sourceType;
    /** 工单正文来源的工单 ID */
    private Long requirementId;
    /** 工单编号 */
    private String requirementNo;
    /** 工单名称 */
    private String requirementTitle;
    /** 命中内容类型：body、image_ocr、image_caption 或 body_image。 */
    private String contentType;
    /** 命中片段数量 */
    private Integer hitCount;
    /** 最大相关度 */
    private Double maxScore;
    /** 正文图片定位信息。 */
    private Long imageFileId;
    private Integer imagePosition;
    private String focus;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Long getRequirementId() { return requirementId; }
    public void setRequirementId(Long requirementId) { this.requirementId = requirementId; }
    public String getRequirementNo() { return requirementNo; }
    public void setRequirementNo(String requirementNo) { this.requirementNo = requirementNo; }
    public String getRequirementTitle() { return requirementTitle; }
    public void setRequirementTitle(String requirementTitle) { this.requirementTitle = requirementTitle; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Integer getHitCount() { return hitCount; }
    public void setHitCount(Integer hitCount) { this.hitCount = hitCount; }
    public Double getMaxScore() { return maxScore; }
    public void setMaxScore(Double maxScore) { this.maxScore = maxScore; }
    public Long getImageFileId() { return imageFileId; }
    public void setImageFileId(Long imageFileId) { this.imageFileId = imageFileId; }
    public Integer getImagePosition() { return imagePosition; }
    public void setImagePosition(Integer imagePosition) { this.imagePosition = imagePosition; }
    public String getFocus() { return focus; }
    public void setFocus(String focus) { this.focus = focus; }
}
