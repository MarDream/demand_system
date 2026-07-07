package com.demand.system.module.statistics.service.impl;

import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.requirement.mapper.RequirementPendingTaskMapper;
import com.demand.system.module.requirement.service.impl.RequirementServiceImpl;
import com.demand.system.module.statistics.dto.BurndownPoint;
import com.demand.system.module.statistics.dto.CfdPoint;
import com.demand.system.module.statistics.dto.DashboardData;
import com.demand.system.module.statistics.dto.DistributionData;
import com.demand.system.module.statistics.dto.DurationData;
import com.demand.system.module.statistics.mapper.StatisticsMapper;
import com.demand.system.module.statistics.service.StatisticsService;
import com.demand.system.module.workflow.dto.WorkflowProcessStatsDTO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StatisticsServiceImpl implements StatisticsService {

    private final StatisticsMapper statisticsMapper;
    private final RequirementPendingTaskMapper pendingTaskMapper;
    private final RequirementServiceImpl requirementService;

    public StatisticsServiceImpl(StatisticsMapper statisticsMapper, RequirementPendingTaskMapper pendingTaskMapper, RequirementServiceImpl requirementService) {
        this.statisticsMapper = statisticsMapper;
        this.pendingTaskMapper = pendingTaskMapper;
        this.requirementService = requirementService;
    }

    @Override
    public Map<String, Object> getDashboardData(Long projectId, Long userId) {
        List<String> currentRoleCodes = SecurityUtils.getCurrentUserRoles();
        boolean isSuperAdmin = RequirementServiceImpl.isSuperAdmin(currentRoleCodes);
        List<Long> visibleOrgIds = requirementService.resolveVisibleOrgIds(userId, isSuperAdmin);

        // 非超级管理员且无可见组织时，统计数均为0
        int totalReqs;
        int inProgressReqs;
        int completedReqs;
        int overdueReqs;
        if (!isSuperAdmin && visibleOrgIds.isEmpty()) {
            totalReqs = 0;
            inProgressReqs = 0;
            completedReqs = 0;
            overdueReqs = 0;
        } else {
            totalReqs = statisticsMapper.getTotalCountWithOrgFilter(userId, visibleOrgIds, isSuperAdmin);
            inProgressReqs = statisticsMapper.getInProgressCountWithOrgFilter(userId, visibleOrgIds, isSuperAdmin);
            completedReqs = statisticsMapper.getCompletedCountWithOrgFilter(userId, visibleOrgIds, isSuperAdmin);
            overdueReqs = statisticsMapper.getOverdueCountWithOrgFilter(userId, visibleOrgIds, isSuperAdmin);
        }
        int myTodoCount;
        if (!isSuperAdmin && visibleOrgIds.isEmpty()) {
            // 非超级管理员且无数据权限：待办数也按 0 处理（与"全部需求"保持一致）
            myTodoCount = 0;
        } else {
            // myTodoCount 是"我作为指派人的待办"统计，按 org 过滤（与"我的待办"列表保持一致）
            myTodoCount = statisticsMapper.getMyTodoCountWithOrgFilter(userId, isSuperAdmin, visibleOrgIds);
        }

        DashboardData data = DashboardData.builder()
                .totalReqs(totalReqs)
                .inProgressReqs(inProgressReqs)
                .completedReqs(completedReqs)
                .overdueReqs(overdueReqs)
                .myTodoCount(myTodoCount)
                .build();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalReqs", data.getTotalReqs());
        result.put("inProgressReqs", data.getInProgressReqs());
        result.put("completedReqs", data.getCompletedReqs());
        result.put("overdueReqs", data.getOverdueReqs());
        result.put("myTodoCount", data.getMyTodoCount());
        return result;
    }

    @Override
    public Map<String, Object> getDistributionData(Long projectId) {
        Long userId = SecurityUtils.getCurrentUserId();
        List<String> currentRoleCodes = SecurityUtils.getCurrentUserRoles();
        boolean isSuperAdmin = RequirementServiceImpl.isSuperAdmin(currentRoleCodes);
        List<Long> visibleOrgIds = requirementService.resolveVisibleOrgIds(userId, isSuperAdmin);

        Map<String, Integer> statusDist = convertToMap(statisticsMapper.getStatusDistributionWithOrgFilter(projectId, isSuperAdmin, visibleOrgIds));
        Map<String, Integer> typeDist = convertToMap(statisticsMapper.getTypeDistributionWithOrgFilter(projectId, isSuperAdmin, visibleOrgIds));
        Map<String, Integer> priorityDist = convertToMap(statisticsMapper.getPriorityDistributionWithOrgFilter(projectId, isSuperAdmin, visibleOrgIds));

        DistributionData data = DistributionData.builder()
                .statusDist(statusDist)
                .typeDist(typeDist)
                .priorityDist(priorityDist)
                .assigneeDist(new LinkedHashMap<>())
                .build();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("statusDist", data.getStatusDist());
        result.put("typeDist", data.getTypeDist());
        result.put("priorityDist", data.getPriorityDist());
        result.put("assigneeDist", data.getAssigneeDist());
        return result;
    }

    @Override
    public List<Map<String, Object>> getDurationData(Long projectId) {
        Long userId = SecurityUtils.getCurrentUserId();
        List<String> currentRoleCodes = SecurityUtils.getCurrentUserRoles();
        boolean isSuperAdmin = RequirementServiceImpl.isSuperAdmin(currentRoleCodes);
        List<Long> visibleOrgIds = requirementService.resolveVisibleOrgIds(userId, isSuperAdmin);
        List<Map<String, Object>> rawList = statisticsMapper.getDurationDataWithOrgFilter(projectId, isSuperAdmin, visibleOrgIds);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> row : rawList) {
            Map<String, Object> item = new LinkedHashMap<>();
            String status = (String) row.get("status");
            BigDecimal avgDays = row.get("avg_days") != null ? new BigDecimal(row.get("avg_days").toString()) : BigDecimal.ZERO;
            BigDecimal maxDays = row.get("max_days") != null ? new BigDecimal(row.get("max_days").toString()) : BigDecimal.ZERO;
            BigDecimal minDays = row.get("min_days") != null ? new BigDecimal(row.get("min_days").toString()) : BigDecimal.ZERO;

            item.put("stateName", status);
            item.put("avgHours", avgDays.multiply(BigDecimal.valueOf(24)));
            item.put("maxHours", maxDays.multiply(BigDecimal.valueOf(24)));
            item.put("minHours", minDays.multiply(BigDecimal.valueOf(24)));
            result.add(item);
        }
        return result;
    }

    @Override
    public List<BurndownPoint> getBurndownData(Long iterationId) {
        Long userId = SecurityUtils.getCurrentUserId();
        List<String> currentRoleCodes = SecurityUtils.getCurrentUserRoles();
        boolean isSuperAdmin = RequirementServiceImpl.isSuperAdmin(currentRoleCodes);
        List<Long> visibleOrgIds = requirementService.resolveVisibleOrgIds(userId, isSuperAdmin);
        List<Map<String, Object>> rawList = statisticsMapper.getBurndownDataWithOrgFilter(iterationId, isSuperAdmin, visibleOrgIds);
        List<BurndownPoint> result = new ArrayList<>();

        int cumulativeTotal = 0;
        int cumulativeCompleted = 0;

        for (Map<String, Object> row : rawList) {
            String date = row.get("date") != null ? row.get("date").toString() : "";
            Number totalNum = (Number) row.get("total");
            Number completedNum = (Number) row.get("completed");
            int total = totalNum != null ? totalNum.intValue() : 0;
            int completed = completedNum != null ? completedNum.intValue() : 0;

            cumulativeTotal += total;
            cumulativeCompleted += completed;

            BurndownPoint point = BurndownPoint.builder()
                    .date(date)
                    .total(cumulativeTotal)
                    .completed(cumulativeCompleted)
                    .remaining(cumulativeTotal - cumulativeCompleted)
                    .build();
            result.add(point);
        }
        return result;
    }

    @Override
    public List<CfdPoint> getCfdData(Long projectId) {
        Long userId = SecurityUtils.getCurrentUserId();
        List<String> currentRoleCodes = SecurityUtils.getCurrentUserRoles();
        boolean isSuperAdmin = RequirementServiceImpl.isSuperAdmin(currentRoleCodes);
        List<Long> visibleOrgIds = requirementService.resolveVisibleOrgIds(userId, isSuperAdmin);
        List<Map<String, Object>> rawList = statisticsMapper.getCfdDataWithOrgFilter(projectId, isSuperAdmin, visibleOrgIds);

        // Group by date, accumulate counts per state per date
        Map<String, Map<String, Long>> dateStateCounts = new LinkedHashMap<>();
        for (Map<String, Object> row : rawList) {
            String date = row.get("date") != null ? row.get("date").toString() : "";
            String status = row.get("status") != null ? row.get("status").toString() : "";
            Number countNum = (Number) row.get("count");
            long count = countNum != null ? countNum.longValue() : 0;

            dateStateCounts.putIfAbsent(date, new LinkedHashMap<>());
            Map<String, Long> stateMap = dateStateCounts.get(date);
            stateMap.put(status, stateMap.getOrDefault(status, 0L) + count);
        }

        // Build cumulative CFD data
        Set<String> allStates = new LinkedHashSet<>();
        dateStateCounts.values().forEach(m -> allStates.addAll(m.keySet()));

        // Cumulative counts per state
        Map<String, Long> cumulativeCounts = new LinkedHashMap<>();
        for (String state : allStates) {
            cumulativeCounts.put(state, 0L);
        }

        List<CfdPoint> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, Long>> entry : dateStateCounts.entrySet()) {
            String date = entry.getKey();
            Map<String, Long> dayCounts = entry.getValue();

            for (String state : allStates) {
                long dayCount = dayCounts.getOrDefault(state, 0L);
                cumulativeCounts.put(state, cumulativeCounts.get(state) + dayCount);
            }

            CfdPoint point = new CfdPoint();
            point.setDate(date);
            Map<String, Integer> stateMap = new LinkedHashMap<>();
            for (Map.Entry<String, Long> ce : cumulativeCounts.entrySet()) {
                stateMap.put(ce.getKey(), ce.getValue().intValue());
            }
            point.setNewData(stateMap);
            result.add(point);
        }
        return result;
    }

    private Map<String, Integer> convertToMap(List<Map<String, Object>> list) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map<String, Object> row : list) {
            String key = row.get("status") != null ? row.get("status").toString()
                    : row.get("type") != null ? row.get("type").toString()
                    : row.keySet().iterator().hasNext() ? row.keySet().iterator().next().toString() : "";
            Number countNum = (Number) row.get("count");
            int count = countNum != null ? countNum.intValue() : 0;
            result.put(key, count);
        }
        return result;
    }

    @Override
    public WorkflowProcessStatsDTO getWorkflowProcessStats(Long userId) {
        List<String> currentRoleCodes = SecurityUtils.getCurrentUserRoles();
        boolean isSuperAdmin = RequirementServiceImpl.isSuperAdmin(currentRoleCodes);
        List<Long> visibleOrgIds = requirementService.resolveVisibleOrgIds(userId, isSuperAdmin);

        // 非超级管理员且未配置数据权限：仅待办数（基于用户）有内容，已办/发起/抄送均为 0
        boolean noDataScope = !isSuperAdmin && visibleOrgIds.isEmpty();
        // 待办数：从 requirement_pending_tasks 表统计（按当前用户，带 org 过滤）
        Long pending = noDataScope ? 0L : pendingTaskMapper.countByUserIdWithOrgFilter(userId, isSuperAdmin, visibleOrgIds);

        // 已办数：我参与过的非草稿需求（带数据权限过滤）
        Long processed = noDataScope ? 0L : statisticsMapper.countProcessedByUserIdWithOrgFilter(userId, isSuperAdmin, visibleOrgIds);

        // 我发起的：creator_id = userId 的非草稿需求（带数据权限过滤）
        Long initiated = noDataScope ? 0L : statisticsMapper.countInitiatedByUserIdWithOrgFilter(userId, isSuperAdmin, visibleOrgIds);

        // 抄送我的：cc_user_ids 包含当前用户的需求（带数据权限过滤）
        Long cc = noDataScope ? 0L : statisticsMapper.countCcByUserIdWithOrgFilter(userId, isSuperAdmin, visibleOrgIds);

        return new WorkflowProcessStatsDTO(pending, processed, initiated, cc);
    }

    @Override
    public Map<String, Long> getTabBadgeCounts(Long userId) {
        List<String> currentRoleCodes = SecurityUtils.getCurrentUserRoles();
        boolean isSuperAdmin = RequirementServiceImpl.isSuperAdmin(currentRoleCodes);
        List<Long> visibleOrgIds = requirementService.resolveVisibleOrgIds(userId, isSuperAdmin);

        // 非超级管理员且未配置数据权限：所有 Tab 角标按 0 处理
        boolean noDataScope = !isSuperAdmin && visibleOrgIds.isEmpty();

        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("pending", noDataScope ? 0L : pendingTaskMapper.countByUserIdWithOrgFilter(userId, isSuperAdmin, visibleOrgIds));
        counts.put("follows", noDataScope ? 0L : statisticsMapper.countMyFollowsByUserIdWithOrgFilter(userId, isSuperAdmin, visibleOrgIds));
        counts.put("drafts", statisticsMapper.countMyDraftsByUserId(userId));
        return counts;
    }

    @Override
    public List<String> getEndNodeStatusCodes() {
        return statisticsMapper.getEndNodeStatusCodes();
    }
}
