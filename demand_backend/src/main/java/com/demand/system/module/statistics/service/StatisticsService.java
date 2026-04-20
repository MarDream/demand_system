package com.demand.system.module.statistics.service;

import com.demand.system.module.statistics.dto.BurndownPoint;
import com.demand.system.module.statistics.dto.CfdPoint;

import java.util.List;
import java.util.Map;

public interface StatisticsService {
    Map<String, Object> getDashboardData(Long projectId, Long userId);
    Map<String, Object> getDistributionData(Long projectId);
    List<Map<String, Object>> getDurationData(Long projectId);
    List<BurndownPoint> getBurndownData(Long iterationId);
    List<CfdPoint> getCfdData(Long projectId);
}
