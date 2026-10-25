package com.demand.system.module.requirement.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.requirement.dto.LowRatingRequirementVO;
import com.demand.system.module.requirement.dto.RequirementRatingStatisticsVO;
import com.demand.system.module.requirement.service.RequirementRatingStatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/statistics/rating")
public class RequirementRatingStatisticsController {

    @Autowired
    private RequirementRatingStatisticsService statisticsService;

    @GetMapping
    public Result<RequirementRatingStatisticsVO> getStatistics(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long iterationId,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(statisticsService.getStatistics(projectId, iterationId, startDate, endDate));
    }

    @GetMapping("/trend")
    public Result<List<RequirementRatingStatisticsVO.RatingTrendPoint>> getTrend(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false, defaultValue = "WEEK") String granularity) {
        return Result.success(statisticsService.getRatingTrend(projectId, startDate, endDate, granularity));
    }

    @GetMapping("/distribution")
    public Result<Map<Integer, Long>> getDistribution(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(statisticsService.getRatingDistribution(projectId, startDate, endDate));
    }

    @GetMapping("/dimensions")
    public Result<Map<String, Double>> getDimensionAverages(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(statisticsService.getDimensionAverages(projectId, startDate, endDate));
    }

    @GetMapping("/low-rated")
    public Result<List<LowRatingRequirementVO>> getLowRated(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false, defaultValue = "3") Integer threshold,
            @RequestParam(required = false, defaultValue = "10") Integer limit) {
        return Result.success(statisticsService.getLowRatingRequirements(projectId, startDate, endDate, threshold, limit));
    }

    @GetMapping("/node-averages")
    public Result<Map<String, Double>> getNodeAverages(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long workflowVersionId,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(statisticsService.getNodeAverageRatings(projectId, workflowVersionId, startDate, endDate));
    }
}
