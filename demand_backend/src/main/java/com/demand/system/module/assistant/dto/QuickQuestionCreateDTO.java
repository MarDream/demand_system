package com.demand.system.module.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 后台创建/编辑快捷问题
 */
public class QuickQuestionCreateDTO {
    @NotBlank
    @Size(max = 500)
    private String questionText;

    private String category = "manual_curated";
    private String pageRoute;
    private Integer weight = 50;
    private Integer sortOrder = 0;
    private String status = "enabled";

    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getPageRoute() { return pageRoute; }
    public void setPageRoute(String pageRoute) { this.pageRoute = pageRoute; }
    public Integer getWeight() { return weight; }
    public void setWeight(Integer weight) { this.weight = weight; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
