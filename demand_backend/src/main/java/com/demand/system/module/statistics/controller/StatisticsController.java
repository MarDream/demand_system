package com.demand.system.module.statistics.controller;

import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.statistics.dto.BurndownPoint;
import com.demand.system.module.statistics.dto.CfdPoint;
import com.demand.system.module.statistics.service.StatisticsService;
import com.demand.system.module.workflow.dto.WorkflowProcessStatsDTO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/projects/{id}/stats/dashboard")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> getDashboard(@PathVariable("id") Long projectId) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return Result.success(statisticsService.getDashboardData(projectId, userId));
    }

    @GetMapping("/projects/{id}/stats/distribution")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> getDistribution(@PathVariable("id") Long projectId) {
        return Result.success(statisticsService.getDistributionData(projectId));
    }

    @GetMapping("/projects/{id}/stats/duration")
    @PreAuthorize("isAuthenticated()")
    public Result<List<Map<String, Object>>> getDuration(@PathVariable("id") Long projectId) {
        return Result.success(statisticsService.getDurationData(projectId));
    }

    @GetMapping("/iterations/{iterationId}/stats/burndown")
    @PreAuthorize("isAuthenticated()")
    public Result<List<Map<String, Object>>> getBurndown(@PathVariable("iterationId") Long iterationId) {
        List<BurndownPoint> data = statisticsService.getBurndownData(iterationId);
        return Result.success(data.stream().map(this::pointToMap).toList());
    }

    @GetMapping("/projects/{id}/stats/cfd")
    @PreAuthorize("isAuthenticated()")
    public Result<List<Map<String, Object>>> getCfd(@PathVariable("id") Long projectId) {
        List<CfdPoint> data = statisticsService.getCfdData(projectId);
        return Result.success(data.stream().map(this::cfdPointToMap).toList());
    }

    @GetMapping("/workflow/process-stats")
    @PreAuthorize("isAuthenticated()")
    public Result<WorkflowProcessStatsDTO> getWorkflowProcessStats() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return Result.success(statisticsService.getWorkflowProcessStats(userId));
    }

    /**
     * Tab 角标计数（轻量版）
     */
    @GetMapping("/tab-badge-counts")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Long>> getTabBadgeCounts() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return Result.success(statisticsService.getTabBadgeCounts(userId));
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
