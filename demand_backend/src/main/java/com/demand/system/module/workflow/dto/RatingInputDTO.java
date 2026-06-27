package com.demand.system.module.workflow.dto;

import java.io.Serializable;
import java.util.Map;

/**
 * 流转请求中的评分数据
 * 支持单维（rating）和多维（ratingDimensions）评分
 */
public class RatingInputDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer rating;
    private Map<String, Integer> ratingDimensions;

    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public Map<String, Integer> getRatingDimensions() { return ratingDimensions; }
    public void setRatingDimensions(Map<String, Integer> ratingDimensions) { this.ratingDimensions = ratingDimensions; }
}
