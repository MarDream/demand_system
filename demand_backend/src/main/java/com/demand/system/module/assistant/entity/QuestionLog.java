package com.demand.system.module.assistant.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import java.util.Objects;

@TableName("question_logs")
public class QuestionLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long orgId;
    private Long sessionId;
    private String pageRoute;
    private String questionText;
    private String questionHash;
    private Integer responseRating;
    private Integer reportCount;
    private Integer tokenCost;
    private Integer answered;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public String getPageRoute() { return pageRoute; }
    public void setPageRoute(String pageRoute) { this.pageRoute = pageRoute; }
    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }
    public String getQuestionHash() { return questionHash; }
    public void setQuestionHash(String questionHash) { this.questionHash = questionHash; }
    public Integer getResponseRating() { return responseRating; }
    public void setResponseRating(Integer responseRating) { this.responseRating = responseRating; }
    public Integer getReportCount() { return reportCount; }
    public void setReportCount(Integer reportCount) { this.reportCount = reportCount; }
    public Integer getTokenCost() { return tokenCost; }
    public void setTokenCost(Integer tokenCost) { this.tokenCost = tokenCost; }
    public Integer getAnswered() { return answered; }
    public void setAnswered(Integer answered) { this.answered = answered; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QuestionLog that = (QuestionLog) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
