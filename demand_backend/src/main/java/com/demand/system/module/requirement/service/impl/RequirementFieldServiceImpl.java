package com.demand.system.module.requirement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.requirement.dto.CustomFieldConfigDTO;
import com.demand.system.module.requirement.dto.CustomFieldValueDTO;
import com.demand.system.module.requirement.dto.FieldOption;
import com.demand.system.module.requirement.entity.CustomField;
import com.demand.system.module.requirement.entity.CustomFieldMultiValue;
import com.demand.system.module.requirement.entity.CustomFieldValue;
import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.requirement.mapper.CustomFieldMapper;
import com.demand.system.module.requirement.mapper.CustomFieldMultiValueMapper;
import com.demand.system.module.requirement.mapper.CustomFieldValueMapper;
import com.demand.system.module.requirement.service.RequirementFieldService;
import com.demand.system.module.workflow.engine.WorkflowVersionResolver;
import com.demand.system.module.workflow.entity.WorkflowEdge;
import com.demand.system.module.workflow.entity.WorkflowInstance;
import com.demand.system.module.workflow.entity.WorkflowNode;
import com.demand.system.module.workflow.entity.WorkflowNodePermission;
import com.demand.system.module.workflow.entity.WorkflowVersion;
import com.demand.system.module.workflow.mapper.WorkflowEdgeMapper;
import com.demand.system.module.workflow.mapper.WorkflowInstanceMapper;
import com.demand.system.module.workflow.mapper.WorkflowNodeMapper;
import com.demand.system.module.workflow.mapper.WorkflowNodePermissionMapper;
import com.demand.system.module.workflow.support.WorkflowNodeUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 需求动态字段服务实现。
 * <p>字段类型：TEXT / SELECT / DATE / NUMBER / MULTI_SELECT / USER / MULTI_USER / BOOLEAN / URL / FILE。
 * <p>单值入主值表（typed column），多选/多人/附件入多值明细表。
 */
@Service
public class RequirementFieldServiceImpl implements RequirementFieldService {

    public static final String TYPE_TEXT = "TEXT";
    public static final String TYPE_SELECT = "SELECT";
    public static final String TYPE_DATE = "DATE";
    public static final String TYPE_NUMBER = "NUMBER";
    public static final String TYPE_MULTI_SELECT = "MULTI_SELECT";
    public static final String TYPE_USER = "USER";
    public static final String TYPE_MULTI_USER = "MULTI_USER";
    public static final String TYPE_BOOLEAN = "BOOLEAN";
    public static final String TYPE_URL = "URL";
    public static final String TYPE_FILE = "FILE";

    private static final String MULTI_OPTION = "OPTION";
    private static final String MULTI_USER = "USER";
    private static final String MULTI_FILE = "FILE";

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final CustomFieldMapper customFieldMapper;
    private final CustomFieldValueMapper customFieldValueMapper;
    private final CustomFieldMultiValueMapper customFieldMultiValueMapper;
    private final WorkflowVersionResolver workflowVersionResolver;
    private final WorkflowNodePermissionMapper workflowNodePermissionMapper;
    private final WorkflowNodeMapper workflowNodeMapper;
    private final WorkflowEdgeMapper workflowEdgeMapper;
    private final WorkflowInstanceMapper workflowInstanceMapper;
    private final ObjectMapper objectMapper;

    public RequirementFieldServiceImpl(CustomFieldMapper customFieldMapper,
                                       CustomFieldValueMapper customFieldValueMapper,
                                       CustomFieldMultiValueMapper customFieldMultiValueMapper,
                                       WorkflowVersionResolver workflowVersionResolver,
                                       WorkflowNodePermissionMapper workflowNodePermissionMapper,
                                       WorkflowNodeMapper workflowNodeMapper,
                                       WorkflowEdgeMapper workflowEdgeMapper,
                                       WorkflowInstanceMapper workflowInstanceMapper,
                                       ObjectMapper objectMapper) {
        this.customFieldMapper = customFieldMapper;
        this.customFieldValueMapper = customFieldValueMapper;
        this.customFieldMultiValueMapper = customFieldMultiValueMapper;
        this.workflowVersionResolver = workflowVersionResolver;
        this.workflowNodePermissionMapper = workflowNodePermissionMapper;
        this.workflowNodeMapper = workflowNodeMapper;
        this.workflowEdgeMapper = workflowEdgeMapper;
        this.workflowInstanceMapper = workflowInstanceMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<CustomField> listEnabledFields(String typeCode) {
        return customFieldMapper.selectList(new LambdaQueryWrapper<CustomField>()
                .and(w -> w.isNull(CustomField::getRequirementTypeCode)
                        .or().eq(CustomField::getRequirementTypeCode, typeCode))
                .eq(CustomField::getEnabled, 1)
                .orderByAsc(CustomField::getSortOrder)
                .orderByAsc(CustomField::getId));
    }

    @Override
    public WorkflowNodePermission resolveCreatePermission(String typeCode) {
        Optional<WorkflowVersion> versionOpt = workflowVersionResolver.findActiveVersionForType(typeCode);
        if (versionOpt.isEmpty()) {
            return null;
        }
        WorkflowVersion version = versionOpt.get();
        WorkflowNode initialNode = findInitialWaitNode(version.getId());
        if (initialNode == null || !StringUtils.hasText(initialNode.getNodeId())) {
            return null;
        }
        return workflowNodePermissionMapper.selectOne(new LambdaQueryWrapper<WorkflowNodePermission>()
                .eq(WorkflowNodePermission::getWorkflowVersionId, version.getId())
                .eq(WorkflowNodePermission::getNodeId, initialNode.getNodeId().trim())
                .last("LIMIT 1"));
    }

    @Override
    public WorkflowNodePermission resolvePermission(Long workflowVersionId, String nodeId) {
        if (workflowVersionId == null || !StringUtils.hasText(nodeId)) {
            return null;
        }
        return workflowNodePermissionMapper.selectOne(new LambdaQueryWrapper<WorkflowNodePermission>()
                .eq(WorkflowNodePermission::getWorkflowVersionId, workflowVersionId)
                .eq(WorkflowNodePermission::getNodeId, nodeId.trim())
                .last("LIMIT 1"));
    }

    @Override
    public WorkflowNodePermission resolveRequirementPermission(Requirement requirement) {
        if (requirement == null || requirement.getWorkflowInstanceId() == null) {
            return null;
        }
        WorkflowInstance instance = workflowInstanceMapper.selectById(requirement.getWorkflowInstanceId());
        if (instance == null || !StringUtils.hasText(instance.getCurrentNodeId())) {
            return null;
        }
        return workflowNodePermissionMapper.selectOne(new LambdaQueryWrapper<WorkflowNodePermission>()
                .eq(WorkflowNodePermission::getWorkflowVersionId, instance.getWorkflowVersionId())
                .eq(WorkflowNodePermission::getNodeId, instance.getCurrentNodeId().trim())
                .last("LIMIT 1"));
    }

    @Override
    public Map<Long, WorkflowNodePermission> resolvePermissions(List<Requirement> requirements) {
        Map<Long, WorkflowNodePermission> result = new HashMap<>();
        if (requirements == null || requirements.isEmpty()) {
            return result;
        }
        Set<Long> instanceIds = requirements.stream()
                .map(Requirement::getWorkflowInstanceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (instanceIds.isEmpty()) {
            return result;
        }
        Map<Long, WorkflowInstance> instances = workflowInstanceMapper.selectBatchIds(instanceIds).stream()
                .filter(i -> i != null && StringUtils.hasText(i.getCurrentNodeId()))
                .collect(Collectors.toMap(WorkflowInstance::getId, i -> i, (a, b) -> a));
        if (instances.isEmpty()) {
            return result;
        }

        // 按 (版本, 节点) 批量取权限快照：每个工作流版本一条 IN 查询
        Map<Long, Set<String>> nodeIdsByVersion = new HashMap<>();
        for (WorkflowInstance instance : instances.values()) {
            nodeIdsByVersion.computeIfAbsent(instance.getWorkflowVersionId(), k -> new LinkedHashSet<>())
                    .add(instance.getCurrentNodeId().trim());
        }
        Map<String, WorkflowNodePermission> permByKey = new HashMap<>();
        for (Map.Entry<Long, Set<String>> entry : nodeIdsByVersion.entrySet()) {
            List<WorkflowNodePermission> perms = workflowNodePermissionMapper.selectList(
                    new LambdaQueryWrapper<WorkflowNodePermission>()
                            .eq(WorkflowNodePermission::getWorkflowVersionId, entry.getKey())
                            .in(WorkflowNodePermission::getNodeId, entry.getValue()));
            for (WorkflowNodePermission perm : perms) {
                if (perm != null && StringUtils.hasText(perm.getNodeId())) {
                    permByKey.putIfAbsent(entry.getKey() + ":" + perm.getNodeId().trim(), perm);
                }
            }
        }

        for (Requirement requirement : requirements) {
            WorkflowInstance instance = requirement.getWorkflowInstanceId() == null
                    ? null : instances.get(requirement.getWorkflowInstanceId());
            if (instance == null) {
                continue;
            }
            WorkflowNodePermission perm =
                    permByKey.get(instance.getWorkflowVersionId() + ":" + instance.getCurrentNodeId().trim());
            if (perm != null) {
                result.put(requirement.getId(), perm);
            }
        }
        return result;
    }

    @Override
    public List<CustomFieldConfigDTO> buildSchema(List<CustomField> fields, WorkflowNodePermission permission,
                                                  Map<String, CustomFieldValueDTO> currentValues) {
        if (fields == null || fields.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> visible = deDup(permission == null
                ? Collections.emptyList() : parseStringList(permission.getVisibleFields(), permission.getEditableFields()));
        List<String> editable = deDup(permission == null
                ? Collections.emptyList() : parseStringList(permission.getEditableFields(), null));
        List<String> required = deDup(permission == null
                ? Collections.emptyList() : parseStringList(permission.getRequiredFields(), null));
        // 可见性唯一来源是节点权限：只要解析到节点权限（含三类均为空的节点）即严格生效，
        // 未声明的字段不展示。仅当无任何节点权限（legacy 无工作流需求）时宽松放行全部字段。
        boolean strict = permission != null;

        List<CustomFieldConfigDTO> result = new ArrayList<>();
        for (CustomField field : fields) {
            String code = field.getFieldCode();
            if (strict && !visible.contains(code) && !editable.contains(code) && !required.contains(code)) {
                continue;
            }
            CustomFieldConfigDTO dto = new CustomFieldConfigDTO();
            dto.setId(field.getId());
            dto.setFieldCode(field.getFieldCode());
            dto.setRequirementTypeCode(field.getRequirementTypeCode());
            dto.setName(field.getName());
            dto.setFieldType(field.getFieldType());
            dto.setOptions(field.getOptions());
            dto.setOptionList(parseFieldOptions(field.getOptions()));
            dto.setRequired(Integer.valueOf(1).equals(field.getRequired()));
            dto.setDefaultValue(field.getDefaultValue());
            dto.setSortOrder(field.getSortOrder());
            dto.setEnabled(Integer.valueOf(1).equals(field.getEnabled()));
            dto.setVisible(true);
            dto.setEditable(!strict || editable.contains(code));
            dto.setRequired(Boolean.TRUE.equals(dto.getRequired()) || required.contains(code));

            CustomFieldValueDTO value = currentValues == null ? null : currentValues.get(code);
            applyValueToSchema(dto, value);
            result.add(dto);
        }
        return result;
    }

    @Override
    @Transactional
    public void validateAndPersist(Requirement requirement, WorkflowNodePermission permission,
                                   List<CustomFieldValueDTO> submitted) {
        List<CustomField> fields = listEnabledFields(requirement.getType());
        if (fields.isEmpty() || submitted == null || submitted.isEmpty()) {
            return;
        }
        Map<String, CustomField> byCode = fields.stream()
                .collect(Collectors.toMap(CustomField::getFieldCode, f -> f, (a, b) -> a));
        List<String> editable = permission == null
                ? Collections.emptyList() : parseStringList(permission.getEditableFields(), null);
        List<String> required = permission == null
                ? Collections.emptyList() : parseStringList(permission.getRequiredFields(), null);
        List<String> visible = permission == null
                ? Collections.emptyList() : parseStringList(permission.getVisibleFields(), null);
        // 与 buildSchema 保持一致：节点未配置字段权限时宽松放行
        boolean strict = !(visible.isEmpty() && editable.isEmpty() && required.isEmpty());

        // 1) 校验提交字段。按字段 upsert 语义：只处理本次提交的字段，未提交字段保留库中原值；
        //    严格模式下不可编辑字段静默忽略（不校验不写入），避免与「只能提交可编辑字段」的客户端互相冲突。
        Map<String, CustomFieldValueDTO> payload = new LinkedHashMap<>();
        for (CustomFieldValueDTO item : submitted) {
            if (item == null || !StringUtils.hasText(item.getFieldCode())) {
                continue;
            }
            CustomField field = byCode.get(item.getFieldCode());
            if (field == null) {
                throw new BusinessException("未知动态字段: " + item.getFieldCode());
            }
            if (strict && !editable.contains(field.getFieldCode())) {
                continue;
            }
            validateValue(field, item);
            payload.put(field.getFieldCode(), item);
        }

        // 2) 必填校验（服务端，严格模式）：本次提交值优先，未提交字段以库中现值为准
        Map<String, CustomFieldValueDTO> existing = required.isEmpty()
                ? Collections.emptyMap() : loadValues(requirement.getId());
        for (String reqCode : required) {
            CustomField field = byCode.get(reqCode);
            if (field == null) {
                continue;
            }
            CustomFieldValueDTO value = payload.get(reqCode);
            if (value == null) {
                value = existing.get(reqCode);
            }
            if (value == null || isEmptyValue(field, value)) {
                throw new BusinessException("必填字段未填写: " + field.getName() + "(" + field.getFieldCode() + ")");
            }
        }

        // 3) 按字段持久化：仅清理并重写本次提交涉及的字段值（提交空值即显式清空该字段）
        for (Map.Entry<String, CustomFieldValueDTO> entry : payload.entrySet()) {
            CustomField field = byCode.get(entry.getKey());
            customFieldValueMapper.delete(new LambdaQueryWrapper<CustomFieldValue>()
                    .eq(CustomFieldValue::getRequirementId, requirement.getId())
                    .eq(CustomFieldValue::getFieldId, field.getId()));
            customFieldMultiValueMapper.delete(new LambdaQueryWrapper<CustomFieldMultiValue>()
                    .eq(CustomFieldMultiValue::getRequirementId, requirement.getId())
                    .eq(CustomFieldMultiValue::getFieldId, field.getId()));
            persist(field, requirement.getId(), entry.getValue());
        }
    }

    @Override
    public Map<String, CustomFieldValueDTO> loadValues(Long requirementId) {
        Map<String, CustomFieldValueDTO> result = new LinkedHashMap<>();
        List<CustomFieldValue> values = customFieldValueMapper.selectList(
                new LambdaQueryWrapper<CustomFieldValue>()
                        .eq(CustomFieldValue::getRequirementId, requirementId));
        // 需要 fieldCode：优先用快照，缺失时兜底查询字段
        Map<Long, String> codeMap = new HashMap<>();
        for (CustomFieldValue value : values) {
            String code = value.getFieldCodeSnapshot();
            if (!StringUtils.hasText(code)) {
                Long fieldId = value.getFieldId();
                code = codeMap.computeIfAbsent(fieldId, this::loadFieldCode);
            }
            if (!StringUtils.hasText(code)) {
                continue;
            }
            result.put(code, toValueDTO(value));
        }

        List<CustomFieldMultiValue> multiValues = customFieldMultiValueMapper.selectList(
                new LambdaQueryWrapper<CustomFieldMultiValue>()
                        .eq(CustomFieldMultiValue::getRequirementId, requirementId)
                        .orderByAsc(CustomFieldMultiValue::getValueType)
                        .orderByAsc(CustomFieldMultiValue::getSortOrder));
        for (CustomFieldMultiValue multi : multiValues) {
            Long fieldId = multi.getFieldId();
            String code = codeMap.computeIfAbsent(fieldId, this::loadFieldCode);
            if (!StringUtils.hasText(code)) {
                continue;
            }
            CustomFieldValueDTO dto = result.computeIfAbsent(code, k -> {
                CustomFieldValueDTO d = new CustomFieldValueDTO();
                d.setFieldCode(code);
                return d;
            });
            if (dto.getValues() == null) {
                dto.setValues(new ArrayList<>());
            }
            dto.getValues().add(multi.getValueText());
        }
        return result;
    }

    @Override
    public Map<Long, Map<String, CustomFieldValueDTO>> loadValuesByRequirementIds(List<Long> requirementIds) {
        Map<Long, Map<String, CustomFieldValueDTO>> result = new HashMap<>();
        if (requirementIds == null || requirementIds.isEmpty()) {
            return result;
        }
        List<CustomFieldValue> values = customFieldValueMapper.selectList(
                new LambdaQueryWrapper<CustomFieldValue>()
                        .in(CustomFieldValue::getRequirementId, requirementIds));
        List<CustomFieldMultiValue> multiValues = customFieldMultiValueMapper.selectList(
                new LambdaQueryWrapper<CustomFieldMultiValue>()
                        .in(CustomFieldMultiValue::getRequirementId, requirementIds)
                        .orderByAsc(CustomFieldMultiValue::getValueType)
                        .orderByAsc(CustomFieldMultiValue::getSortOrder));
        if (values.isEmpty() && multiValues.isEmpty()) {
            return result;
        }

        // fieldId → fieldCode：优先用值行快照，缺失的字段一次性批量补齐
        Map<Long, String> codeMap = new HashMap<>();
        Set<Long> missingFieldIds = new HashSet<>();
        for (CustomFieldValue value : values) {
            if (value.getFieldId() == null) {
                continue;
            }
            if (StringUtils.hasText(value.getFieldCodeSnapshot())) {
                codeMap.put(value.getFieldId(), value.getFieldCodeSnapshot());
            } else {
                missingFieldIds.add(value.getFieldId());
            }
        }
        for (CustomFieldMultiValue multi : multiValues) {
            if (multi.getFieldId() != null) {
                missingFieldIds.add(multi.getFieldId());
            }
        }
        missingFieldIds.removeAll(codeMap.keySet());
        if (!missingFieldIds.isEmpty()) {
            for (CustomField field : customFieldMapper.selectBatchIds(missingFieldIds)) {
                if (field != null) {
                    codeMap.put(field.getId(), field.getFieldCode());
                }
            }
        }

        for (CustomFieldValue value : values) {
            String code = codeMap.get(value.getFieldId());
            if (!StringUtils.hasText(code)) {
                continue;
            }
            result.computeIfAbsent(value.getRequirementId(), k -> new LinkedHashMap<>())
                    .put(code, toValueDTO(value));
        }
        for (CustomFieldMultiValue multi : multiValues) {
            String code = codeMap.get(multi.getFieldId());
            if (!StringUtils.hasText(code)) {
                continue;
            }
            Map<String, CustomFieldValueDTO> perRequirement =
                    result.computeIfAbsent(multi.getRequirementId(), k -> new LinkedHashMap<>());
            CustomFieldValueDTO dto = perRequirement.computeIfAbsent(code, k -> {
                CustomFieldValueDTO d = new CustomFieldValueDTO();
                d.setFieldCode(code);
                return d;
            });
            if (dto.getValues() == null) {
                dto.setValues(new ArrayList<>());
            }
            dto.getValues().add(multi.getValueText());
        }
        return result;
    }

    @Override
    public List<FieldOption> parseFieldOptions(String optionsJson) {
        if (!StringUtils.hasText(optionsJson)) {
            return Collections.emptyList();
        }
        try {
            JsonNode node = objectMapper.readTree(optionsJson);
            if (!node.isArray()) {
                return Collections.emptyList();
            }
            List<FieldOption> result = new ArrayList<>();
            Set<String> seenKeys = new LinkedHashSet<>();
            for (JsonNode item : node) {
                if (item.isTextual()) {
                    // 历史形态：字符串数组，key = label
                    String text = item.asText().trim();
                    if (StringUtils.hasText(text) && seenKeys.add(text)) {
                        result.add(new FieldOption(text, text));
                    }
                } else if (item.isObject()) {
                    String key = item.path("key").asText("").trim();
                    String label = item.path("label").asText("").trim();
                    if (StringUtils.hasText(key) && seenKeys.add(key)) {
                        result.add(new FieldOption(key, StringUtils.hasText(label) ? label : key));
                    }
                }
            }
            return result;
        } catch (Exception ignore) {
            return Collections.emptyList();
        }
    }

    @Override
    public String normalizeOptionsJson(String optionsJson) {
        List<FieldOption> options = parseFieldOptions(optionsJson);
        if (options.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(options);
        } catch (Exception ignore) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private String loadFieldCode(Long fieldId) {
        if (fieldId == null) {
            return null;
        }
        CustomField field = customFieldMapper.selectById(fieldId);
        return field == null ? null : field.getFieldCode();
    }

    private CustomFieldValueDTO toValueDTO(CustomFieldValue value) {
        CustomFieldValueDTO dto = new CustomFieldValueDTO();
        dto.setFieldCode(value.getFieldCodeSnapshot());
        dto.setValue(value.getValueText());
        dto.setValueNumber(value.getValueNumber());
        dto.setValueDate(value.getValueDate());
        dto.setValueBoolean(value.getValueBoolean() != null && value.getValueBoolean() == 1);
        dto.setValueUserId(value.getValueUserId());
        return dto;
    }

    private void applyValueToSchema(CustomFieldConfigDTO dto, CustomFieldValueDTO value) {
        if (value == null) {
            return;
        }
        dto.setValue(value.getValue());
        dto.setValueNumber(value.getValueNumber());
        dto.setValueDate(value.getValueDate());
        dto.setValueBoolean(value.getValueBoolean());
        dto.setValueUserId(value.getValueUserId());
        dto.setValues(value.getValues());
    }

    private void persist(CustomField field, Long requirementId, CustomFieldValueDTO value) {
        String type = normalizeType(field.getFieldType());
        switch (type) {
            case TYPE_MULTI_SELECT -> persistMulti(requirementId, field, MULTI_OPTION, stringValues(value));
            case TYPE_MULTI_USER -> persistMulti(requirementId, field, MULTI_USER, stringValues(value));
            case TYPE_FILE -> persistMulti(requirementId, field, MULTI_FILE, stringValues(value));
            default -> {
                CustomFieldValue row = new CustomFieldValue();
                row.setRequirementId(requirementId);
                row.setFieldId(field.getId());
                row.setFieldCodeSnapshot(field.getFieldCode());
                switch (type) {
                    case TYPE_NUMBER -> row.setValueNumber(value.getValueNumber());
                    case TYPE_DATE -> row.setValueDate(value.getValueDate());
                    case TYPE_BOOLEAN -> row.setValueBoolean(Boolean.TRUE.equals(value.getValueBoolean()) ? 1 : 0);
                    case TYPE_USER -> row.setValueUserId(value.getValueUserId());
                    default -> row.setValueText(value.getValue());
                }
                row.setUpdatedAt(LocalDateTime.now());
                customFieldValueMapper.insert(row);
            }
        }
    }

    private void persistMulti(Long requirementId, CustomField field, String valueType, List<String> items) {
        if (items == null) {
            return;
        }
        int order = 0;
        for (String item : items) {
            if (!StringUtils.hasText(item)) {
                continue;
            }
            CustomFieldMultiValue row = new CustomFieldMultiValue();
            row.setRequirementId(requirementId);
            row.setFieldId(field.getId());
            row.setValueType(valueType);
            row.setValueText(item.trim());
            row.setSortOrder(order++);
            row.setCreatedAt(LocalDateTime.now());
            customFieldMultiValueMapper.insert(row);
        }
    }

    private List<String> stringValues(CustomFieldValueDTO value) {
        List<Object> values = value.getValues();
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return values.stream().map(String::valueOf).toList();
    }

    private void validateValue(CustomField field, CustomFieldValueDTO value) {
        String type = normalizeType(field.getFieldType());
        switch (type) {
            case TYPE_NUMBER -> {
                if (value.getValueNumber() == null && isNotEmptyString(value.getValue())) {
                    throw new BusinessException("字段为数值类型: " + field.getName());
                }
            }
            case TYPE_DATE -> {
                if (value.getValueDate() == null && isNotEmptyString(value.getValue())) {
                    throw new BusinessException("字段为日期类型: " + field.getName());
                }
            }
            case TYPE_BOOLEAN -> {
                // 允许 null 作为未设置
            }
            case TYPE_USER -> {
                if (value.getValueUserId() == null && isNotEmptyString(value.getValue())) {
                    throw new BusinessException("字段为人员类型: " + field.getName());
                }
            }
            case TYPE_SELECT, TYPE_MULTI_SELECT -> validateOptions(field, value);
            case TYPE_URL -> {
                String v = value.getValue();
                if (StringUtils.hasText(v) && !isValidUrl(v)) {
                    throw new BusinessException("URL格式不合法: " + field.getName());
                }
            }
            default -> {
                // TEXT / FILE 等
            }
        }
    }

    private void validateOptions(CustomField field, CustomFieldValueDTO value) {
        List<FieldOption> allowed = parseFieldOptions(field.getOptions());
        if (allowed.isEmpty()) {
            return;
        }
        String type = normalizeType(field.getFieldType());
        List<String> toCheck;
        if (TYPE_MULTI_SELECT.equals(type)) {
            toCheck = stringValues(value);
        } else {
            toCheck = StringUtils.hasText(value.getValue())
                    ? List.of(value.getValue()) : Collections.emptyList();
        }
        // 字段值存的是选项 key（历史数据为裸文本，key=label 时天然兼容）
        Set<String> allowedKeys = allowed.stream().map(FieldOption::getKey).collect(Collectors.toSet());
        for (String item : toCheck) {
            if (StringUtils.hasText(item) && !allowedKeys.contains(item.trim())) {
                throw new BusinessException("非法选项值: " + field.getName() + " = " + item);
            }
        }
    }

    private boolean isNotEmptyString(String s) {
        return s != null && !s.isBlank();
    }

    private boolean isValidUrl(String url) {
        String lower = url.toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private boolean isEmptyValue(CustomField field, CustomFieldValueDTO value) {
        String type = normalizeType(field.getFieldType());
        return switch (type) {
            case TYPE_NUMBER -> value.getValueNumber() == null;
            case TYPE_DATE -> value.getValueDate() == null;
            case TYPE_BOOLEAN -> value.getValueBoolean() == null;
            case TYPE_USER -> value.getValueUserId() == null;
            case TYPE_MULTI_SELECT, TYPE_MULTI_USER, TYPE_FILE ->
                    value.getValues() == null || value.getValues().isEmpty();
            default -> !StringUtils.hasText(value.getValue());
        };
    }

    private String normalizeType(String type) {
        return type == null ? TYPE_TEXT : type.trim().toUpperCase();
    }

    private List<String> parseStringList(String raw, String fallbackRaw) {
        List<String> primary = parseStringList(raw);
        if (!primary.isEmpty()) {
            return deDup(primary);
        }
        if (StringUtils.hasText(fallbackRaw)) {
            return deDup(parseStringList(fallbackRaw));
        }
        return Collections.emptyList();
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
                    .toList();
        } catch (Exception ignore) {
            return java.util.Arrays.stream(raw.replace("[", "").replace("]", "").replace("\"", "").split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .toList();
        }
    }

    private List<String> deDup(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        return raw.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new)).stream().toList();
    }

    private WorkflowNode findInitialWaitNode(Long workflowVersionId) {
        List<WorkflowNode> nodes = workflowNodeMapper.selectList(new LambdaQueryWrapper<WorkflowNode>()
                .eq(WorkflowNode::getWorkflowVersionId, workflowVersionId)
                .orderByAsc(WorkflowNode::getId));
        if (nodes.isEmpty()) {
            return null;
        }
        Map<String, WorkflowNode> nodeById = new HashMap<>();
        for (WorkflowNode node : nodes) {
            if (node != null && StringUtils.hasText(node.getNodeId())) {
                nodeById.put(node.getNodeId(), node);
            }
        }
        WorkflowNode startNode = nodes.stream()
                .filter(node -> "start".equalsIgnoreCase(node.getNodeType()))
                .findFirst().orElse(null);
        if (startNode == null || !StringUtils.hasText(startNode.getNodeId())) {
            return null;
        }
        List<WorkflowEdge> edges = workflowEdgeMapper.selectList(new LambdaQueryWrapper<WorkflowEdge>()
                .eq(WorkflowEdge::getWorkflowVersionId, workflowVersionId)
                .orderByAsc(WorkflowEdge::getId));
        Map<String, List<WorkflowEdge>> outgoing = edges.stream()
                .filter(e -> StringUtils.hasText(e.getSourceNodeId()) && StringUtils.hasText(e.getTargetNodeId()))
                .collect(Collectors.groupingBy(WorkflowEdge::getSourceNodeId));
        ArrayDeque<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(startNode.getNodeId());
        while (!queue.isEmpty()) {
            String currentNodeId = queue.removeFirst();
            if (!visited.add(currentNodeId)) {
                continue;
            }
            for (WorkflowEdge edge : outgoing.getOrDefault(currentNodeId, Collections.emptyList())) {
                WorkflowNode target = nodeById.get(edge.getTargetNodeId());
                if (target == null) {
                    continue;
                }
                if (WorkflowNodeUtils.isWaitNode(target.getNodeType()) && !"start".equalsIgnoreCase(target.getNodeType())) {
                    return target;
                }
                queue.addLast(target.getNodeId());
            }
        }
        return null;
    }
}