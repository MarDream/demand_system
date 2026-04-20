package com.demand.system.module.statistics.service.impl;

import com.demand.system.module.statistics.dto.BurndownPoint;
import com.demand.system.module.statistics.dto.CfdPoint;
import com.demand.system.module.statistics.dto.DashboardData;
import com.demand.system.module.statistics.dto.DistributionData;
import com.demand.system.module.statistics.dto.DurationData;
import com.demand.system.module.statistics.mapper.StatisticsMapper;
import com.demand.system.module.statistics.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final StatisticsMapper statisticsMapper;

    @Override
    public Map<String, Object> getDashboardData(Long projectId, Long userId) {
        int totalReqs = statisticsMapper.getTotalCount(projectId);
        int inProgressReqs = statisticsMapper.getInProgressCount(projectId);
        int completedReqs = statisticsMapper.getCompletedCount(projectId);
        int overdueReqs = statisticsMapper.getOverdueCount(projectId);
        int myTodoCount = statisticsMapper.getMyTodoCount(userId);

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
        Map<String, Integer> statusDist = convertToMap(statisticsMapper.getStatusDistribution(projectId));
        Map<String, Integer> typeDist = convertToMap(statisticsMapper.getTypeDistribution(projectId));
        Map<String, Integer> priorityDist = convertToMap(statisticsMapper.getPriorityDistribution(projectId));

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
        List<Map<String, Object>> rawList = statisticsMapper.getDurationData(projectId);
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
        List<Map<String, Object>> rawList = statisticsMapper.getBurndownData(iterationId);
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
        List<Map<String, Object>> rawList = statisticsMapper.getCfdData(projectId);

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
}
