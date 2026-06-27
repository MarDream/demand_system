package com.demand.system.module.requirement.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 需求评分统计 VO
 * 对应 ADR-002 工作流节点评分功能设计
 */
public class RequirementRatingStatisticsVO {

    /**
     * 总体平均分
     */
    private Double overallAverage;

    /**
     * 各维度平均分
     * key: 维度标识(如 quality, response_speed)
     * value: 该维度的平均分
     */
    private Map<String, Double> dimensionAverages;

    /**
     * 评分趋势（按周/月）
     */
    private List<RatingTrendPoint> trends;

    /**
     * 评分分布（1星:10, 2星:20, ...）
     */
    private Map<Integer, Long> distribution;

    /**
     * Top 10 低分需求
     */
    private List<LowRatingRequirementVO> topLowRated;

    /**
     * 各节点平均分
     * key: 节点名称
     * value: 该节点的平均分
     */
    private Map<String, Double> nodeAverages;

    /**
     * 统计周期开始时间
     */
    private LocalDateTime periodStart;

    /**
     * 统计周期结束时间
     */
    private LocalDateTime periodEnd;

    /**
     * 总评价数
     */
    private Long totalEvaluations;

    // Getters and Setters

    public Double getOverallAverage() {
        return overallAverage;
    }

    public void setOverallAverage(Double overallAverage) {
        this.overallAverage = overallAverage;
    }

    public Map<String, Double> getDimensionAverages() {
        return dimensionAverages;
    }

    public void setDimensionAverages(Map<String, Double> dimensionAverages) {
        this.dimensionAverages = dimensionAverages;
    }

    public List<RatingTrendPoint> getTrends() {
        return trends;
    }

    public void setTrends(List<RatingTrendPoint> trends) {
        this.trends = trends;
    }

    public Map<Integer, Long> getDistribution() {
        return distribution;
    }

    public void setDistribution(Map<Integer, Long> distribution) {
        this.distribution = distribution;
    }

    public List<LowRatingRequirementVO> getTopLowRated() {
        return topLowRated;
    }

    public void setTopLowRated(List<LowRatingRequirementVO> topLowRated) {
        this.topLowRated = topLowRated;
    }

    public Map<String, Double> getNodeAverages() {
        return nodeAverages;
    }

    public void setNodeAverages(Map<String, Double> nodeAverages) {
        this.nodeAverages = nodeAverages;
    }

    public LocalDateTime getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDateTime periodStart) {
        this.periodStart = periodStart;
    }

    public LocalDateTime getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDateTime periodEnd) {
        this.periodEnd = periodEnd;
    }

    public Long getTotalEvaluations() {
        return totalEvaluations;
    }

    public void setTotalEvaluations(Long totalEvaluations) {
        this.totalEvaluations = totalEvaluations;
    }

    /**
     * 评分趋势点
     */
    public static class RatingTrendPoint {
        /**
         * 时间点标签（如 "2026-W25", "2026-06"）
         */
        private String label;

        /**
         * 该时间点的平均分
         */
        private Double average;

        /**
         * 该时间点的评价数
         */
        private Long count;

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public Double getAverage() {
            return average;
        }

        public void setAverage(Double average) {
            this.average = average;
        }

        public Long getCount() {
            return count;
        }

        public void setCount(Long count) {
            this.count = count;
        }
    }
}
