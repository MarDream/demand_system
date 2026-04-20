package com.demand.system.module.iteration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.iteration.dto.IterationCreateDTO;
import com.demand.system.module.iteration.dto.IterationUpdateDTO;
import com.demand.system.module.iteration.dto.IterationVO;
import com.demand.system.module.iteration.entity.Iteration;
import com.demand.system.module.iteration.mapper.IterationMapper;
import com.demand.system.module.iteration.service.IterationService;
import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.requirement.mapper.RequirementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IterationServiceImpl implements IterationService {

    private final IterationMapper iterationMapper;
    private final RequirementMapper requirementMapper;

    @Override
    public List<IterationVO> listByProject(Long projectId) {
        LambdaQueryWrapper<Iteration> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Iteration::getProjectId, projectId)
                .eq(Iteration::getDeletedAt, 0)
                .orderByDesc(Iteration::getCreatedAt);

        List<Iteration> iterations = iterationMapper.selectList(wrapper);
        List<IterationVO> voList = new ArrayList<>();
        for (Iteration it : iterations) {
            voList.add(buildVO(it));
        }
        return voList;
    }

    @Override
    public IterationVO getById(Long id) {
        Map<String, Object> detail = iterationMapper.selectDetailById(id);
        if (detail == null) {
            throw new BusinessException("迭代不存在");
        }

        IterationVO vo = new IterationVO();
        vo.setId(((Number) detail.get("id")).longValue());
        vo.setProjectId(detail.get("project_id") != null ? ((Number) detail.get("project_id")).longValue() : null);
        vo.setName((String) detail.get("name"));
        vo.setDescription((String) detail.get("description"));
        if (detail.get("start_date") != null) {
            vo.setStartDate(java.time.LocalDate.parse(detail.get("start_date").toString()));
        }
        if (detail.get("end_date") != null) {
            vo.setEndDate(java.time.LocalDate.parse(detail.get("end_date").toString()));
        }
        if (detail.get("capacity") != null) {
            vo.setCapacity(new BigDecimal(detail.get("capacity").toString()));
        }
        vo.setStatus((String) detail.get("status"));
        if (detail.get("creator_id") != null) {
            vo.setCreatorId(((Number) detail.get("creator_id")).longValue());
        }
        if (detail.get("created_at") != null) {
            vo.setCreatedAt(java.time.LocalDateTime.parse(detail.get("created_at").toString().replace(' ', 'T')));
        }
        if (detail.get("updated_at") != null) {
            vo.setUpdatedAt(java.time.LocalDateTime.parse(detail.get("updated_at").toString().replace(' ', 'T')));
        }
        vo.setRequirementCount(detail.get("requirement_count") != null ? ((Number) detail.get("requirement_count")).intValue() : 0);
        vo.setCompletedCount(detail.get("completed_count") != null ? ((Number) detail.get("completed_count")).intValue() : 0);

        if (vo.getRequirementCount() != null && vo.getRequirementCount() > 0) {
            vo.setProgress((double) vo.getCompletedCount() / vo.getRequirementCount() * 100.0);
        } else {
            vo.setProgress(0.0);
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(IterationCreateDTO dto, Long userId) {
        Iteration iteration = new Iteration();
        BeanUtils.copyProperties(dto, iteration);
        iteration.setCreatorId(userId);
        iteration.setStatus("未开始");
        iterationMapper.insert(iteration);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(IterationUpdateDTO dto) {
        Iteration existing = iterationMapper.selectById(dto.getId());
        if (existing == null) {
            throw new BusinessException("迭代不存在");
        }

        UpdateWrapper<Iteration> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", dto.getId());

        if (dto.getName() != null) {
            updateWrapper.set("name", dto.getName());
        }
        if (dto.getDescription() != null) {
            updateWrapper.set("description", dto.getDescription());
        }
        if (dto.getStartDate() != null) {
            updateWrapper.set("start_date", dto.getStartDate());
        }
        if (dto.getEndDate() != null) {
            updateWrapper.set("end_date", dto.getEndDate());
        }
        if (dto.getCapacity() != null) {
            updateWrapper.set("capacity", dto.getCapacity());
        }
        if (dto.getStatus() != null) {
            updateWrapper.set("status", dto.getStatus());
        }

        iterationMapper.update(null, updateWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Iteration iteration = iterationMapper.selectById(id);
        if (iteration == null) {
            throw new BusinessException("迭代不存在");
        }
        iterationMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRequirements(Long iterationId, List<Long> requirementIds) {
        Iteration iteration = iterationMapper.selectById(iterationId);
        if (iteration == null) {
            throw new BusinessException("迭代不存在");
        }

        UpdateWrapper<Requirement> wrapper = new UpdateWrapper<>();
        wrapper.in("id", requirementIds)
                .set("iteration_id", iterationId);
        requirementMapper.update(null, wrapper);
    }

    @Override
    public Map<String, Object> getBurndownData(Long iterationId) {
        Iteration iteration = iterationMapper.selectById(iterationId);
        if (iteration == null) {
            throw new BusinessException("迭代不存在");
        }

        LambdaQueryWrapper<Requirement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Requirement::getIterationId, iterationId)
                .eq(Requirement::getDeletedAt, 0)
                .orderByAsc(Requirement::getCreatedAt);

        List<Requirement> requirements = requirementMapper.selectList(wrapper);

        // Group by created date for total line
        Map<String, Long> createdPerDay = requirements.stream()
                .filter(r -> r.getCreatedAt() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getCreatedAt().toLocalDate().toString(),
                        Collectors.counting()
                ));

        // Group completed requirements by date (when status changed to completed)
        // Since we only have updatedAt, approximate by using updated requirements that are in completed status
        Map<String, Long> completedPerDay = new LinkedHashMap<>();
        for (Requirement r : requirements) {
            if ("已上线".equals(r.getStatus()) || "已验收".equals(r.getStatus())) {
                String dateStr = r.getUpdatedAt() != null
                        ? r.getUpdatedAt().toLocalDate().toString()
                        : r.getCreatedAt().toLocalDate().toString();
                completedPerDay.merge(dateStr, 1L, Long::sum);
            }
        }

        // Build sorted date range
        List<String> allDates = new ArrayList<>(new TreeSet<>(createdPerDay.keySet()));
        allDates.addAll(new TreeSet<>(completedPerDay.keySet()));
        allDates = new ArrayList<>(new LinkedHashSet<>(allDates));
        Collections.sort(allDates);

        List<Map<String, Object>> series = new ArrayList<>();
        long cumulativeTotal = 0;
        long cumulativeCompleted = 0;

        for (String date : allDates) {
            cumulativeTotal += createdPerDay.getOrDefault(date, 0L);
            cumulativeCompleted += completedPerDay.getOrDefault(date, 0L);

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", date);
            point.put("total", cumulativeTotal);
            point.put("completed", cumulativeCompleted);
            series.add(point);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("iterationId", iterationId);
        result.put("totalRequirements", requirements.size());
        result.put("series", series);
        return result;
    }

    private IterationVO buildVO(Iteration it) {
        IterationVO vo = new IterationVO();
        BeanUtils.copyProperties(it, vo);

        int reqCount = iterationMapper.countRequirements(it.getId());
        vo.setRequirementCount(reqCount);

        // Count completed requirements
        LambdaQueryWrapper<Requirement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Requirement::getIterationId, it.getId())
                .eq(Requirement::getDeletedAt, 0)
                .in(Requirement::getStatus, "已上线", "已验收");
        int completedCount = requirementMapper.selectCount(wrapper).intValue();
        vo.setCompletedCount(completedCount);

        if (reqCount > 0) {
            vo.setProgress((double) completedCount / reqCount * 100.0);
        } else {
            vo.setProgress(0.0);
        }
        return vo;
    }
}
