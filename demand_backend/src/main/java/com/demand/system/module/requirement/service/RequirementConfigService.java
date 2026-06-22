package com.demand.system.module.requirement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demand.system.common.result.Result;
import com.demand.system.module.requirement.dto.RequirementFieldAlias;
import com.demand.system.module.requirement.dto.SortRequest;
import com.demand.system.module.requirement.dto.RequirementFormConfigDTO;
import com.demand.system.module.requirement.entity.PriorityConfig;
import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.requirement.entity.RequirementTypeConfig;
import com.demand.system.module.requirement.mapper.PriorityMapper;
import com.demand.system.module.requirement.mapper.RequirementMapper;
import com.demand.system.module.requirement.mapper.RequirementTypeMapper;
import com.demand.system.module.workflow.entity.WorkflowNode;
import com.demand.system.module.workflow.entity.WorkflowNodePermission;
import com.demand.system.module.workflow.entity.WorkflowState;
import com.demand.system.module.workflow.entity.WorkflowTransition;
import com.demand.system.module.workflow.engine.WorkflowVersionResolver;
import com.demand.system.module.workflow.entity.WorkflowVersion;
import com.demand.system.module.workflow.mapper.WorkflowNodeMapper;
import com.demand.system.module.workflow.mapper.WorkflowNodePermissionMapper;
import com.demand.system.module.workflow.mapper.WorkflowStateMapper;
import com.demand.system.module.workflow.mapper.WorkflowTransitionMapper;
import com.demand.system.module.workflow.mapper.WorkflowVersionMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class RequirementConfigService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final RequirementTypeMapper typeMapper;
    private final PriorityMapper priorityMapper;
    private final RequirementMapper requirementMapper;
    private final WorkflowVersionMapper workflowVersionMapper;
    private final WorkflowStateMapper workflowStateMapper;
    private final WorkflowVersionResolver workflowVersionResolver;
    private final WorkflowTransitionMapper workflowTransitionMapper;
    private final WorkflowNodePermissionMapper workflowNodePermissionMapper;
    private final WorkflowNodeMapper workflowNodeMapper;
    private final ObjectMapper objectMapper;

    public RequirementConfigService(RequirementTypeMapper typeMapper, PriorityMapper priorityMapper, RequirementMapper requirementMapper, WorkflowVersionMapper workflowVersionMapper, WorkflowStateMapper workflowStateMapper, WorkflowVersionResolver workflowVersionResolver, WorkflowTransitionMapper workflowTransitionMapper, WorkflowNodePermissionMapper workflowNodePermissionMapper, WorkflowNodeMapper workflowNodeMapper, ObjectMapper objectMapper) {
        this.typeMapper = typeMapper;
        this.priorityMapper = priorityMapper;
        this.requirementMapper = requirementMapper;
        this.workflowVersionMapper = workflowVersionMapper;
        this.workflowStateMapper = workflowStateMapper;
        this.workflowVersionResolver = workflowVersionResolver;
        this.workflowTransitionMapper = workflowTransitionMapper;
        this.workflowNodePermissionMapper = workflowNodePermissionMapper;
        this.workflowNodeMapper = workflowNodeMapper;
        this.objectMapper = objectMapper;
    }

    public Result<List<RequirementTypeConfig>> listTypes() {
        List<RequirementTypeConfig> types = typeMapper.selectList(
                new LambdaQueryWrapper<RequirementTypeConfig>()
                        .orderByAsc(RequirementTypeConfig::getSortOrder)
        );
        return Result.success(types);
    }

    /**
     * 仅返回已绑定活跃工作流版本的需求类型。
     * <p>用于创建需求时的类型下拉：未绑定或绑定版本不可用的类型不出现在选项中。
     */
    public Result<List<RequirementTypeConfig>> listAvailableTypes() {
        List<RequirementTypeConfig> allTypes = typeMapper.selectList(
                new LambdaQueryWrapper<RequirementTypeConfig>()
                        .orderByAsc(RequirementTypeConfig::getSortOrder)
        );
        List<RequirementTypeConfig> available = allTypes.stream()
                .filter(type -> workflowVersionResolver.findActiveVersionForType(type.getCode()).isPresent())
                .collect(Collectors.toList());
        return Result.success(available);
    }

    /**
     * 绑定/解绑需求类型到工作流版本。
     *
     * @param typeCode          需求类型编码
     * @param workflowVersionId 工作流版本ID；传 null 表示解绑
     */
    @Transactional
    public Result<Void> bindWorkflow(String typeCode, Long workflowVersionId) {
        RequirementTypeConfig typeConfig = typeMapper.selectByCode(typeCode);
        if (typeConfig == null) {
            return Result.fail("需求类型不存在: " + typeCode);
        }
        if (workflowVersionId != null) {
            WorkflowVersion version = workflowVersionMapper.selectById(workflowVersionId);
            if (version == null) {
                return Result.fail("工作流版本不存在: " + workflowVersionId);
            }
            if (version.getIsActive() == null || version.getIsActive() != 1 || !"active".equals(version.getActivationStatus())) {
                return Result.fail("只能绑定启用中的工作流版本");
            }
        }
        typeConfig.setWorkflowVersionId(workflowVersionId);
        typeMapper.updateById(typeConfig);
        return Result.success();
    }

    public Result<RequirementFormConfigDTO> getCreateFormConfig(Long projectId) {
        // 优先取已绑定活跃工作流的默认 type
        List<RequirementTypeConfig> allTypes = typeMapper.selectList(
                new LambdaQueryWrapper<RequirementTypeConfig>()
                        .orderByAsc(RequirementTypeConfig::getSortOrder)
        );
        RequirementTypeConfig defaultType = allTypes.stream()
                .filter(type -> Boolean.TRUE.equals(type.getIsDefault()))
                .filter(type -> workflowVersionResolver.findActiveVersionForType(type.getCode()).isPresent())
                .findFirst()
                .orElse(null);
        // 如果默认 type 不可用，取第一个可用的
        if (defaultType == null) {
            defaultType = allTypes.stream()
                    .filter(type -> workflowVersionResolver.findActiveVersionForType(type.getCode()).isPresent())
                    .findFirst()
                    .orElse(null);
        }

        RequirementFormConfigDTO config = new RequirementFormConfigDTO();
        if (defaultType != null) {
            config.setDefaultTypeCode(defaultType.getCode());
            config.setDefaultTypeName(defaultType.getName());
            config.setDefaultTypeColor(defaultType.getColor());
        }

        // 按 type 维度解析工作流版本
        WorkflowVersion activeVersion = defaultType != null
                ? workflowVersionResolver.findActiveVersionForType(defaultType.getCode()).orElse(null)
                : null;

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
        config.setVisibleFields(appendCcFieldIfNeeded(normalizeFields(visibleFields), activeVersion.getId()));
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
        if (existsTypeCode(type.getCode(), null)) {
            return Result.fail("需求类型编码已存在");
        }
        if (type.getSortOrder() == null) {
            type.setSortOrder(nextTypeSortOrder());
        }
        syncTypeDefaultFlag(type.getId(), type.getIsDefault());
        typeMapper.insert(type);
        return Result.success();
    }

    @Transactional
    public Result<Void> updateType(RequirementTypeConfig type) {
        RequirementTypeConfig existing = typeMapper.selectById(type.getId());
        if (existing == null) {
            return Result.fail("需求类型不存在");
        }
        if (existsTypeCode(type.getCode(), type.getId())) {
            return Result.fail("需求类型编码已存在");
        }
        syncTypeDefaultFlag(type.getId(), type.getIsDefault());
        typeMapper.updateById(type);
        return Result.success();
    }

    @Transactional
    public Result<Void> deleteType(Long id) {
        RequirementTypeConfig existing = typeMapper.selectById(id);
        if (existing == null) {
            return Result.fail("需求类型不存在");
        }
        long referenceCount = requirementMapper.selectCount(
                new LambdaQueryWrapper<Requirement>()
                        .eq(Requirement::getType, existing.getCode())
        );
        if (referenceCount > 0) {
            return Result.fail("该需求类型已被需求引用，无法删除");
        }
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
        if (existsPriorityCode(priority.getCode(), null)) {
            return Result.fail("优先级编码已存在");
        }
        if (priority.getSortOrder() == null) {
            priority.setSortOrder(nextPrioritySortOrder());
        }
        syncPriorityDefaultFlag(priority.getId(), priority.getIsDefault());
        priorityMapper.insert(priority);
        return Result.success();
    }

    @Transactional
    public Result<Void> updatePriority(PriorityConfig priority) {
        PriorityConfig existing = priorityMapper.selectById(priority.getId());
        if (existing == null) {
            return Result.fail("优先级不存在");
        }
        if (existsPriorityCode(priority.getCode(), priority.getId())) {
            return Result.fail("优先级编码已存在");
        }
        syncPriorityDefaultFlag(priority.getId(), priority.getIsDefault());
        priorityMapper.updateById(priority);
        return Result.success();
    }

    @Transactional
    public Result<Void> deletePriority(Long id) {
        PriorityConfig existing = priorityMapper.selectById(id);
        if (existing == null) {
            return Result.fail("优先级不存在");
        }
        long referenceCount = requirementMapper.selectCount(
                new LambdaQueryWrapper<Requirement>()
                        .eq(Requirement::getPriority, existing.getCode())
        );
        if (referenceCount > 0) {
            return Result.fail("该优先级已被需求引用，无法删除");
        }
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
        Long runtimeProjectId = workflowVersionResolver.resolveRuntimeProjectId(projectId);
        List<WorkflowState> states = workflowStateMapper.selectList(
                new LambdaQueryWrapper<WorkflowState>()
                        .eq(WorkflowState::getProjectId, runtimeProjectId)
                        .orderByAsc(WorkflowState::getSortOrder)
                        .orderByAsc(WorkflowState::getId)
        );
        if (states.isEmpty()) {
            return null;
        }

        List<Long> targetStateIds = workflowTransitionMapper.selectList(
                new LambdaQueryWrapper<WorkflowTransition>()
                        .eq(WorkflowTransition::getProjectId, runtimeProjectId)
        ).stream()
                .map(WorkflowTransition::getToStateId)
                .filter(Objects::nonNull)
                .toList();

        return states.stream()
                .filter(state -> !targetStateIds.contains(state.getId()))
                .findFirst()
                .orElse(states.get(0));
    }



    private boolean existsTypeCode(String code, Long excludeId) {
        LambdaQueryWrapper<RequirementTypeConfig> wrapper = new LambdaQueryWrapper<RequirementTypeConfig>()
                .eq(RequirementTypeConfig::getCode, code);
        if (excludeId != null) {
            wrapper.ne(RequirementTypeConfig::getId, excludeId);
        }
        return typeMapper.selectCount(wrapper) > 0;
    }

    private boolean existsPriorityCode(String code, Long excludeId) {
        LambdaQueryWrapper<PriorityConfig> wrapper = new LambdaQueryWrapper<PriorityConfig>()
                .eq(PriorityConfig::getCode, code);
        if (excludeId != null) {
            wrapper.ne(PriorityConfig::getId, excludeId);
        }
        return priorityMapper.selectCount(wrapper) > 0;
    }

    private Integer nextTypeSortOrder() {
        RequirementTypeConfig last = typeMapper.selectOne(
                new LambdaQueryWrapper<RequirementTypeConfig>()
                        .orderByDesc(RequirementTypeConfig::getSortOrder)
                        .last("LIMIT 1")
        );
        return last == null || last.getSortOrder() == null ? 0 : last.getSortOrder() + 1;
    }

    private Integer nextPrioritySortOrder() {
        PriorityConfig last = priorityMapper.selectOne(
                new LambdaQueryWrapper<PriorityConfig>()
                        .orderByDesc(PriorityConfig::getSortOrder)
                        .last("LIMIT 1")
        );
        return last == null || last.getSortOrder() == null ? 0 : last.getSortOrder() + 1;
    }

    private void syncTypeDefaultFlag(Long currentId, Boolean isDefault) {
        if (!Boolean.TRUE.equals(isDefault)) {
            return;
        }
        LambdaUpdateWrapper<RequirementTypeConfig> wrapper = new LambdaUpdateWrapper<RequirementTypeConfig>()
                .set(RequirementTypeConfig::getIsDefault, false);
        if (currentId != null) {
            wrapper.ne(RequirementTypeConfig::getId, currentId);
        }
        typeMapper.update(null, wrapper);
    }

    private void syncPriorityDefaultFlag(Long currentId, Boolean isDefault) {
        if (!Boolean.TRUE.equals(isDefault)) {
            return;
        }
        LambdaUpdateWrapper<PriorityConfig> wrapper = new LambdaUpdateWrapper<PriorityConfig>()
                .set(PriorityConfig::getIsDefault, false);
        if (currentId != null) {
            wrapper.ne(PriorityConfig::getId, currentId);
        }
        priorityMapper.update(null, wrapper);
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

    private List<String> appendCcFieldIfNeeded(List<String> visibleFields, Long workflowVersionId) {
        if (!hasCcNodeConfigured(workflowVersionId)) {
            return visibleFields;
        }

        LinkedHashSet<String> fields = new LinkedHashSet<>(visibleFields);
        fields.add("ccUserIds");
        return fields.stream().toList();
    }

    private boolean hasCcNodeConfigured(Long workflowVersionId) {
        if (workflowVersionId == null) {
            return false;
        }
        Long count = workflowNodeMapper.selectCount(new LambdaQueryWrapper<WorkflowNode>()
                .eq(WorkflowNode::getWorkflowVersionId, workflowVersionId)
                .eq(WorkflowNode::getNodeType, "cc"));
        return count != null && count > 0;
    }
}
