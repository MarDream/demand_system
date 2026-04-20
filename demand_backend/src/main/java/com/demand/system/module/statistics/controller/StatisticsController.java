package com.demand.system.module.statistics.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.statistics.dto.BurndownPoint;
import com.demand.system.module.statistics.dto.CfdPoint;
import com.demand.system.module.statistics.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    @GetMapping("/{id}/stats/dashboard")
    public Result<Map<String, Object>> getDashboard(@PathVariable("id") Long projectId) {
        Long userId = 1L;
        return Result.success(statisticsService.getDashboardData(projectId, userId));
    }

    @GetMapping("/{id}/stats/distribution")
    public Result<Map<String, Object>> getDistribution(@PathVariable("id") Long projectId) {
        return Result.success(statisticsService.getDistributionData(projectId));
    }

    @GetMapping("/{id}/stats/duration")
    public Result<List<Map<String, Object>>> getDuration(@PathVariable("id") Long projectId) {
        return Result.success(statisticsService.getDurationData(projectId));
    }

    @GetMapping("/iterations/{iterationId}/stats/burndown")
    public Result<List<Map<String, Object>>> getBurndown(@PathVariable("iterationId") Long iterationId) {
        List<BurndownPoint> data = statisticsService.getBurndownData(iterationId);
        return Result.success(data.stream().map(this::pointToMap).toList());
    }

    @GetMapping("/{id}/stats/cfd")
    public Result<List<Map<String, Object>>> getCfd(@PathVariable("id") Long projectId) {
        List<CfdPoint> data = statisticsService.getCfdData(projectId);
        return Result.success(data.stream().map(this::cfdPointToMap).toList());
    }

    private Map<String, Object> pointToMap(BurndownPoint point) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("date", point.getDate());
        map.put("remaining", point.getRemaining());
        map.put("completed", point.getCompleted());
        map.put("total", point.getTotal());
        return map;
    }

    private Map<String, Object> cfdPointToMap(CfdPoint point) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("date", point.getDate());
        map.put("newData", point.getNewData());
        return map;
    }
}
