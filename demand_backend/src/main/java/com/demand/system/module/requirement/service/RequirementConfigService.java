package com.demand.system.module.requirement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.result.Result;
import com.demand.system.module.requirement.entity.PriorityConfig;
import com.demand.system.module.requirement.entity.RequirementTypeConfig;
import com.demand.system.module.requirement.mapper.PriorityMapper;
import com.demand.system.module.requirement.mapper.RequirementTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RequirementConfigService {

    private final RequirementTypeMapper typeMapper;
    private final PriorityMapper priorityMapper;

    public Result<List<RequirementTypeConfig>> listTypes() {
        List<RequirementTypeConfig> types = typeMapper.selectList(
                new LambdaQueryWrapper<RequirementTypeConfig>()
                        .orderByAsc(RequirementTypeConfig::getSortOrder)
        );
        return Result.success(types);
    }

    @Transactional
    public Result<Void> createType(RequirementTypeConfig type) {
        typeMapper.insert(type);
        return Result.success();
    }

    @Transactional
    public Result<Void> updateType(RequirementTypeConfig type) {
        typeMapper.updateById(type);
        return Result.success();
    }

    @Transactional
    public Result<Void> deleteType(Long id) {
        typeMapper.deleteById(id);
        return Result.success();
    }

    public Result<List<PriorityConfig>> listPriorities() {
        List<PriorityConfig> priorities = priorityMapper.selectList(
                new LambdaQueryWrapper<PriorityConfig>()
                        .orderByAsc(PriorityConfig::getSortOrder)
        );
        return Result.success(priorities);
    }

    @Transactional
    public Result<Void> createPriority(PriorityConfig priority) {
        priorityMapper.insert(priority);
        return Result.success();
    }

    @Transactional
    public Result<Void> updatePriority(PriorityConfig priority) {
        priorityMapper.updateById(priority);
        return Result.success();
    }

    @Transactional
    public Result<Void> deletePriority(Long id) {
        priorityMapper.deleteById(id);
        return Result.success();
    }
}