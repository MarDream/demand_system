package com.demand.system.module.assistant.dto;

/**
 * 前台快捷问题视图对象
 */
public class QuickQuestionVO {
    private Long id;
    private String category;
    private String questionText;
    private String pageRoute;
    private Integer weight;
    private Integer sortOrder;
    private Integer hitCount;
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }
    public String getPageRoute() { return pageRoute; }
    public void setPageRoute(String pageRoute) { this.pageRoute = pageRoute; }
    public Integer getWeight() { return weight; }
    public void setWeight(Integer weight) { this.weight = weight; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Integer getHitCount() { return hitCount; }
    public void setHitCount(Integer hitCount) { this.hitCount = hitCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
