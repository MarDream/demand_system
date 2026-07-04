package com.demand.system.module.statistics.service;

import com.demand.system.module.statistics.dto.BurndownPoint;
import com.demand.system.module.statistics.dto.CfdPoint;
import com.demand.system.module.workflow.dto.WorkflowProcessStatsDTO;

import java.util.List;
import java.util.Map;

public interface StatisticsService {
    Map<String, Object> getDashboardData(Long projectId, Long userId);
    Map<String, Object> getDistributionData(Long projectId);
    List<Map<String, Object>> getDurationData(Long projectId);
    List<BurndownPoint> getBurndownData(Long iterationId);
    List<CfdPoint> getCfdData(Long projectId);
    WorkflowProcessStatsDTO getWorkflowProcessStats(Long userId);

    /**
     * 获取 Tab 角标计数（轻量版）
     * 只返回待办、关注、草稿的数量，不走完整分页查询
     */
    Map<String, Long> getTabBadgeCounts(Long userId);
}
