package com.demand.system.module.requirement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.demand.system.module.requirement.dto.LowRatingRequirementVO;
import com.demand.system.module.requirement.dto.RequirementRatingStatisticsVO;
import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.requirement.entity.RequirementApprovalEvaluation;
import com.demand.system.module.requirement.mapper.RequirementApprovalEvaluationMapper;
import com.demand.system.module.requirement.mapper.RequirementMapper;
import com.demand.system.module.requirement.service.RequirementRatingStatisticsService;
import com.demand.system.module.user.entity.User;
import com.demand.system.module.user.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RequirementRatingStatisticsServiceImpl implements RequirementRatingStatisticsService {

    @Autowired
    private RequirementApprovalEvaluationMapper evaluationMapper;

    @Autowired
    private RequirementMapper requirementMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    public RequirementRatingStatisticsVO getStatistics(Long projectId, Long iterationId,
                                                       LocalDate startDate, LocalDate endDate) {
        RequirementRatingStatisticsVO vo = new RequirementRatingStatisticsVO();
        vo.setPeriodStart(startDate != null ? startDate.atStartOfDay() : LocalDateTime.now().minusMonths(1));
        vo.setPeriodEnd(endDate != null ? endDate.atTime(23, 59, 59) : LocalDateTime.now());

        List<RequirementApprovalEvaluation> evaluations = queryEvaluations(projectId, iterationId, startDate, endDate);
        if (evaluations.isEmpty()) {
            vo.setOverallAverage(0.0);
            vo.setTotalEvaluations(0L);
            vo.setDimensionAverages(new HashMap<>());
            vo.setDistribution(buildEmptyDistribution());
            vo.setNodeAverages(new HashMap<>());
            vo.setTopLowRated(new ArrayList<>());
            return vo;
        }

        vo.setTotalEvaluations((long) evaluations.size());

        // 整体平均
        double overall = evaluations.stream()
                .filter(e -> e.getRating() != null)
                .mapToInt(RequirementApprovalEvaluation::getRating)
                .average()
                .orElse(0.0);
        vo.setOverallAverage(round(overall));

        // 维度平均
        vo.setDimensionAverages(getDimensionAverages(projectId, startDate, endDate));

        // 评分分布
        vo.setDistribution(getRatingDistribution(projectId, startDate, endDate));

        // 节点平均
        vo.setNodeAverages(getNodeAverageRatings(projectId, null, startDate, endDate));

        // 低分需求
        vo.setTopLowRated(getLowRatingRequirements(projectId, startDate, endDate, 3, 10));

        return vo;
    }

    @Override
    public List<RequirementRatingStatisticsVO.RatingTrendPoint> getRatingTrend(
            Long projectId, LocalDate startDate, LocalDate endDate, String granularity) {
        if (startDate == null) startDate = LocalDate.now().minusMonths(3);
        if (endDate == null) endDate = LocalDate.now();
        if (!StringUtils.hasText(granularity)) granularity = "WEEK";

        List<RequirementApprovalEvaluation> evaluations = queryEvaluations(projectId, null, startDate, endDate);
        if (evaluations.isEmpty()) return new ArrayList<>();

        // 按时间桶聚合
        Map<String, List<Integer>> buckets = new TreeMap<>();
        for (RequirementApprovalEvaluation e : evaluations) {
            if (e.getRating() == null || e.getCreatedAt() == null) continue;
            String label = formatBucket(e.getCreatedAt().toLocalDate(), granularity);
            buckets.computeIfAbsent(label, k -> new ArrayList<>()).add(e.getRating());
        }

        List<RequirementRatingStatisticsVO.RatingTrendPoint> result = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> entry : buckets.entrySet()) {
            double avg = entry.getValue().stream().mapToInt(Integer::intValue).average().orElse(0.0);
            RequirementRatingStatisticsVO.RatingTrendPoint point = new RequirementRatingStatisticsVO.RatingTrendPoint();
            point.setLabel(entry.getKey());
            point.setAverage(round(avg));
            point.setCount((long) entry.getValue().size());
            result.add(point);
        }
        return result;
    }

    @Override
    public Map<Integer, Long> getRatingDistribution(Long projectId, LocalDate startDate, LocalDate endDate) {
        Map<Integer, Long> dist = buildEmptyDistribution();
        List<RequirementApprovalEvaluation> evaluations = queryEvaluations(projectId, null, startDate, endDate);
        for (RequirementApprovalEvaluation e : evaluations) {
            if (e.getRating() != null && e.getRating() >= 1 && e.getRating() <= 5) {
                dist.merge(e.getRating(), 1L, Long::sum);
            }
        }
        return dist;
    }

    @Override
    public Map<String, Double> getDimensionAverages(Long projectId, LocalDate startDate, LocalDate endDate) {
        Map<String, double[]> sumCount = new HashMap<>();
        List<RequirementApprovalEvaluation> evaluations = queryEvaluations(projectId, null, startDate, endDate);
        for (RequirementApprovalEvaluation e : evaluations) {
            Map<String, Integer> dims = e.getRatingDimensions();
            if (dims == null) continue;
            for (Map.Entry<String, Integer> entry : dims.entrySet()) {
                if (entry.getValue() == null) continue;
                sumCount.computeIfAbsent(entry.getKey(), k -> new double[2])[0] += entry.getValue();
                sumCount.get(entry.getKey())[1] += 1;
            }
        }
        Map<String, Double> result = new LinkedHashMap<>();
        for (Map.Entry<String, double[]> e : sumCount.entrySet()) {
            result.put(e.getKey(), e.getValue()[1] == 0 ? 0.0 : round(e.getValue()[0] / e.getValue()[1]));
        }
        return result;
    }

    @Override
    public List<LowRatingRequirementVO> getLowRatingRequirements(
            Long projectId, LocalDate startDate, LocalDate endDate,
            Integer threshold, Integer limit) {
        if (threshold == null) threshold = 3;
        if (limit == null) limit = 10;
        final int finalThreshold = threshold;
        List<RequirementApprovalEvaluation> evaluations = queryEvaluations(projectId, null, startDate, endDate);
        // 过滤低分
        List<RequirementApprovalEvaluation> low = evaluations.stream()
                .filter(e -> e.getRating() != null && e.getRating() < finalThreshold)
                .sorted(Comparator.comparing(RequirementApprovalEvaluation::getCreatedAt).reversed())
                .limit(limit)
                .collect(Collectors.toList());

        List<LowRatingRequirementVO> result = new ArrayList<>();
        for (RequirementApprovalEvaluation e : low) {
            LowRatingRequirementVO vo = new LowRatingRequirementVO();
            vo.setRequirementId(e.getRequirementId());
            Requirement req = requirementMapper.selectById(e.getRequirementId());
            if (req != null) {
                vo.setRequirementNo(req.getRequirementNo());
                vo.setTitle(req.getTitle());
            }
            vo.setNodeId(e.getNodeId());
            vo.setNodeName(e.getNodeName());
            vo.setRating(e.getRating());
            vo.setRatingDimensions(e.getRatingDimensions());
            vo.setComment(e.getContent());
            vo.setEvaluatorId(e.getEvaluatorId());
            if (e.getEvaluatorId() != null) {
                User u = userMapper.selectById(e.getEvaluatorId());
                if (u != null) vo.setEvaluatorName(u.getRealName() != null ? u.getRealName() : u.getUsername());
            }
            vo.setCreatedAt(e.getCreatedAt());
            result.add(vo);
        }
        return result;
    }

    @Override
    public Map<String, Double> getNodeAverageRatings(Long projectId, Long workflowVersionId,
                                                     LocalDate startDate, LocalDate endDate) {
        Map<String, double[]> sumCount = new HashMap<>();
        List<RequirementApprovalEvaluation> evaluations = queryEvaluations(projectId, null, startDate, endDate);
        for (RequirementApprovalEvaluation e : evaluations) {
            if (e.getRating() == null || !StringUtils.hasText(e.getNodeName())) continue;
            sumCount.computeIfAbsent(e.getNodeName(), k -> new double[2])[0] += e.getRating();
            sumCount.get(e.getNodeName())[1] += 1;
        }
        Map<String, Double> result = new LinkedHashMap<>();
        for (Map.Entry<String, double[]> e : sumCount.entrySet()) {
            result.put(e.getKey(), e.getValue()[1] == 0 ? 0.0 : round(e.getValue()[0] / e.getValue()[1]));
        }
        return result;
    }

    // ==================== 私有工具 ====================

    private List<RequirementApprovalEvaluation> queryEvaluations(
            Long projectId, Long iterationId, LocalDate startDate, LocalDate endDate) {
        QueryWrapper<RequirementApprovalEvaluation> qw = new QueryWrapper<>();
        qw.isNull("parent_id");
        if (startDate != null) qw.ge("created_at", startDate.atStartOfDay());
        if (endDate != null) qw.le("created_at", endDate.atTime(23, 59, 59));
        if (projectId != null || iterationId != null) {
            // 通过需求过滤
            QueryWrapper<Requirement> reqQw = new QueryWrapper<>();
            if (projectId != null) reqQw.eq("project_id", projectId);
            if (iterationId != null) reqQw.eq("iteration_id", iterationId);
            reqQw.select("id");
            List<Object> reqIds = requirementMapper.selectObjs(reqQw);
            if (reqIds.isEmpty()) return new ArrayList<>();
            qw.in("requirement_id", reqIds);
        }
        qw.orderByDesc("created_at");
        return evaluationMapper.selectList(qw);
    }

    private Map<Integer, Long> buildEmptyDistribution() {
        Map<Integer, Long> dist = new LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) dist.put(i, 0L);
        return dist;
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private String formatBucket(LocalDate date, String granularity) {
        if ("MONTH".equalsIgnoreCase(granularity)) {
            return date.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        }
        // WEEK：以周一为起点
        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        long week = ChronoUnit.WEEKS.between(LocalDate.of(2020, 1, 6), monday) + 1;
        return String.format("%d-W%02d", monday.getYear(), week);
    }
}
