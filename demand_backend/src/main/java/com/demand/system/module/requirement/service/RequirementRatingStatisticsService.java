package com.demand.system.module.requirement.service;

import com.demand.system.module.requirement.dto.LowRatingRequirementVO;
import com.demand.system.module.requirement.dto.RequirementRatingStatisticsVO;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 需求评分统计服务
 * 对应 ADR-002 Phase 2: 统计分析
 */
public interface RequirementRatingStatisticsService {

    /**
     * 综合统计：项目/迭代时间范围内的评分
     */
    RequirementRatingStatisticsVO getStatistics(Long projectId, Long iterationId,
                                                LocalDate startDate, LocalDate endDate);

    /**
     * 获取评分趋势（按周）
     */
    List<RequirementRatingStatisticsVO.RatingTrendPoint> getRatingTrend(
            Long projectId, LocalDate startDate, LocalDate endDate, String granularity);

    /**
     * 获取评分分布（1星/2星/.../5星 各数量）
     */
    Map<Integer, Long> getRatingDistribution(Long projectId, LocalDate startDate, LocalDate endDate);

    /**
     * 获取各维度的平均分
     */
    Map<String, Double> getDimensionAverages(Long projectId, LocalDate startDate, LocalDate endDate);

    /**
     * 获取低分需求列表
     */
    List<LowRatingRequirementVO> getLowRatingRequirements(
            Long projectId, LocalDate startDate, LocalDate endDate,
            Integer threshold, Integer limit);

    /**
     * 获取各节点的平均分（识别流程瓶颈）
     */
    Map<String, Double> getNodeAverageRatings(Long projectId, Long workflowVersionId,
                                              LocalDate startDate, LocalDate endDate);
}
