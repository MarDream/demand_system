package com.demand.system.module.assistant.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 自动提炼问题视图对象（后台管理用）
 */
public class ExtractedQuestionVO {
    private String questionText;
    private String questionHash;
    private String pageRoute;
    private Integer frequency;
    private BigDecimal avgRating;
    private BigDecimal aiConfidence;
    private LocalDateTime lastAskedAt;
    private String infoLevel;

    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }
    public String getQuestionHash() { return questionHash; }
    public void setQuestionHash(String questionHash) { this.questionHash = questionHash; }
    public String getPageRoute() { return pageRoute; }
    public void setPageRoute(String pageRoute) { this.pageRoute = pageRoute; }
    public Integer getFrequency() { return frequency; }
    public void setFrequency(Integer frequency) { this.frequency = frequency; }
    public BigDecimal getAvgRating() { return avgRating; }
    public void setAvgRating(BigDecimal avgRating) { this.avgRating = avgRating; }
    public BigDecimal getAiConfidence() { return aiConfidence; }
    public void setAiConfidence(BigDecimal aiConfidence) { this.aiConfidence = aiConfidence; }
    public LocalDateTime getLastAskedAt() { return lastAskedAt; }
    public void setLastAskedAt(LocalDateTime lastAskedAt) { this.lastAskedAt = lastAskedAt; }
    public String getInfoLevel() { return infoLevel; }
    public void setInfoLevel(String infoLevel) { this.infoLevel = infoLevel; }
}
