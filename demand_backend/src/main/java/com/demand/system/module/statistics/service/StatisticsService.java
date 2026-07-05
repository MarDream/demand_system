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

    /**
     * 查询所有标记为结束的节点状态码列表
     * @return 结束状态的 code 列表 (如 [已上线, 已验收, 已取消, 已拒绝])
     */
    List<String> getEndNodeStatusCodes();
}
