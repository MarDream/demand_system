package com.demand.system.module.requirement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.result.Result;
import com.demand.system.module.requirement.dto.RequirementFieldAlias;
import com.demand.system.module.requirement.dto.SortRequest;
import com.demand.system.module.requirement.dto.RequirementFormConfigDTO;
import com.demand.system.module.requirement.entity.PriorityConfig;
import com.demand.system.module.requirement.entity.RequirementTypeConfig;
import com.demand.system.module.requirement.mapper.PriorityMapper;
import com.demand.system.module.requirement.mapper.RequirementTypeMapper;
import com.demand.system.module.workflow.entity.WorkflowNodePermission;
import com.demand.system.module.workflow.entity.WorkflowState;
import com.demand.system.module.workflow.entity.WorkflowTransition;
import com.demand.system.module.workflow.entity.WorkflowVersion;
import com.demand.system.module.workflow.mapper.WorkflowNodePermissionMapper;
import com.demand.system.module.workflow.mapper.WorkflowStateMapper;
import com.demand.system.module.workflow.mapper.WorkflowTransitionMapper;
import com.demand.system.module.workflow.mapper.WorkflowVersionMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RequirementConfigService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final RequirementTypeMapper typeMapper;
    private final PriorityMapper priorityMapper;
    private final WorkflowVersionMapper workflowVersionMapper;
    private final WorkflowStateMapper workflowStateMapper;
    private final WorkflowTransitionMapper workflowTransitionMapper;
    private final WorkflowNodePermissionMapper workflowNodePermissionMapper;
    private final ObjectMapper objectMapper;

    public Result<List<RequirementTypeConfig>> listTypes() {
        List<RequirementTypeConfig> types = typeMapper.selectList(
                new LambdaQueryWrapper<RequirementTypeConfig>()
                        .orderByAsc(RequirementTypeConfig::getSortOrder)
        );
        return Result.success(types);
    }

    public Result<RequirementFormConfigDTO> getCreateFormConfig(Long projectId) {
        RequirementTypeConfig defaultType = typeMapper.selectOne(
                new LambdaQueryWrapper<RequirementTypeConfig>()
                        .orderByAsc(RequirementTypeConfig::getSortOrder)
                        .orderByDesc(RequirementTypeConfig::getIsDefault)
                        .last("LIMIT 1")
        );

        RequirementFormConfigDTO config = new RequirementFormConfigDTO();
        if (defaultType != null) {
            config.setDefaultTypeCode(defaultType.getCode());
            config.setDefaultTypeName(defaultType.getName());
            config.setDefaultTypeColor(defaultType.getColor());
        }

        WorkflowVersion activeVersion = workflowVersionMapper.selectOne(
                new LambdaQueryWrapper<WorkflowVersion>()
                        .eq(WorkflowVersion::getProjectId, projectId)
                        .eq(WorkflowVersion::getIsActive, 1)
                        .orderByDesc(WorkflowVersion::getVersion)
                        .last("LIMIT 1")
        );

        if (activeVersion == null) {
            config.setVisibleFields(Collections.emptyList());
            config.setRequiredFields(Collections.emptyList());
            return Result.success(config);
        }

        WorkflowState initialState = findInitialState(projectId);
        if (initialState == null || !StringUtils.hasText(initialState.getName())) {
            config.setVisibleFields(Collections.emptyList());
            config.setRequiredFields(Collections.emptyList());
            return Result.success(config);
        }

        WorkflowNodePermission permission = workflowNodePermissionMapper.selectOne(
                new LambdaQueryWrapper<WorkflowNodePermission>()
                        .eq(WorkflowNodePermission::getWorkflowVersionId, activeVersion.getId())
                        .eq(WorkflowNodePermission::getNodeId, initialState.getName().trim())
                        .last("LIMIT 1")
        );

        if (permission == null) {
            config.setVisibleFields(Collections.emptyList());
            config.setRequiredFields(Collections.emptyList());
            return Result.success(config);
        }

        List<String> visibleFields = parseStringList(permission.getVisibleFields());
        if (visibleFields.isEmpty()) {
            visibleFields = parseStringList(permission.getEditableFields());
        }
        config.setVisibleFields(normalizeFields(visibleFields));
        config.setRequiredFields(normalizeFields(parseStringList(permission.getRequiredFields())));
        return Result.success(config);
    }

    public RequirementTypeConfig getDefaultType() {
        return typeMapper.selectOne(
                new LambdaQueryWrapper<RequirementTypeConfig>()
                        .orderByAsc(RequirementTypeConfig::getSortOrder)
                        .orderByDesc(RequirementTypeConfig::getIsDefault)
                        .last("LIMIT 1")
        );
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

    @Transactional
    public Result<List<RequirementTypeConfig>> sortTypes(List<SortRequest> sortRequests) {
        if (CollectionUtils.isEmpty(sortRequests)) {
            return Result.fail("排序列表不能为空");
        }

        // 提取所有ID
        List<Long> ids = sortRequests.stream()
                .map(SortRequest::getId)
                .collect(Collectors.toList());

        // 查询所有需要更新的记录
        List<RequirementTypeConfig> types = typeMapper.selectBatchIds(ids);
        if (types.size() != sortRequests.size()) {
            return Result.fail("部分ID不存在");
        }

        // 创建ID到sortOrder的映射
        Map<Long, Integer> sortOrderMap = sortRequests.stream()
                .collect(Collectors.toMap(SortRequest::getId, SortRequest::getSortOrder));

        // 更新sortOrder并逐个更新
        types.forEach(type -> {
            type.setSortOrder(sortOrderMap.get(type.getId()));
            typeMapper.updateById(type);
        });

        // 返回更新后的列表
        return listTypes();
    }

    @Transactional
    public Result<List<PriorityConfig>> sortPriorities(List<SortRequest> sortRequests) {
        if (CollectionUtils.isEmpty(sortRequests)) {
            return Result.fail("排序列表不能为空");
        }

        // 提取所有ID
        List<Long> ids = sortRequests.stream()
                .map(SortRequest::getId)
                .collect(Collectors.toList());

        // 查询所有需要更新的记录
        List<PriorityConfig> priorities = priorityMapper.selectBatchIds(ids);
        if (priorities.size() != sortRequests.size()) {
            return Result.fail("部分ID不存在");
        }

        // 创建ID到sortOrder的映射
        Map<Long, Integer> sortOrderMap = sortRequests.stream()
                .collect(Collectors.toMap(SortRequest::getId, SortRequest::getSortOrder));

        // 更新sortOrder并逐个更新
        priorities.forEach(priority -> {
            priority.setSortOrder(sortOrderMap.get(priority.getId()));
            priorityMapper.updateById(priority);
        });

        // 返回更新后的列表
        return listPriorities();
    }

    private WorkflowState findInitialState(Long projectId) {
        List<WorkflowState> states = workflowStateMapper.selectList(
                new LambdaQueryWrapper<WorkflowState>()
                        .eq(WorkflowState::getProjectId, projectId)
                        .orderByAsc(WorkflowState::getSortOrder)
                        .orderByAsc(WorkflowState::getId)
        );
        if (states.isEmpty()) {
            return null;
        }

        List<Long> targetStateIds = workflowTransitionMapper.selectList(
                new LambdaQueryWrapper<WorkflowTransition>()
                        .eq(WorkflowTransition::getProjectId, projectId)
        ).stream()
                .map(WorkflowTransition::getToStateId)
                .filter(Objects::nonNull)
                .toList();

        return states.stream()
                .filter(state -> !targetStateIds.contains(state.getId()))
                .findFirst()
                .orElse(states.get(0));
    }

    private List<String> parseStringList(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Collections.emptyList();
        }
        try {
            List<String> values = objectMapper.readValue(raw, STRING_LIST);
            return values == null ? Collections.emptyList() : values.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception ignore) {
            return java.util.Arrays.stream(raw.replace("[", "").replace("]", "").replace("\"", "")
                    .split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .toList();
        }
    }

    private List<String> normalizeFields(List<String> rawFields) {
        if (rawFields == null || rawFields.isEmpty()) {
            return Collections.emptyList();
        }
        return rawFields.stream()
                .map(RequirementFieldAlias::normalize)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
    }
}
