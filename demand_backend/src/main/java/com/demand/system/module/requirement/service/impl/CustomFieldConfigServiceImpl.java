package com.demand.system.module.requirement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.Result;
import com.demand.system.module.requirement.dto.CustomFieldConfigDTO;
import com.demand.system.module.requirement.dto.FieldOption;
import com.demand.system.module.requirement.dto.SortItemDTO;
import com.demand.system.module.requirement.entity.CustomField;
import com.demand.system.module.requirement.entity.CustomFieldMultiValue;
import com.demand.system.module.requirement.entity.CustomFieldValue;
import com.demand.system.module.requirement.mapper.CustomFieldMapper;
import com.demand.system.module.requirement.mapper.CustomFieldMultiValueMapper;
import com.demand.system.module.requirement.mapper.CustomFieldValueMapper;
import com.demand.system.module.requirement.service.CustomFieldConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** 需求动态字段定义管理服务实现。 */
@Service
public class CustomFieldConfigServiceImpl implements CustomFieldConfigService {

    /** 允许的字段类型。 */
    private static final List<String> ALLOWED_TYPES = List.of(
            "TEXT", "SELECT", "DATE", "NUMBER",
            "MULTI_SELECT", "USER", "MULTI_USER",
            "BOOLEAN", "URL", "FILE"
    );

    /** 不可用作自定义字段编码的固定系统字段。 */
    private static final Pattern SYSTEM_FIELD_CODES = Pattern.compile(
            "^(id|projectId|title|description|type|priority|status|assigneeId|ccUserIds|" +
            "iterationId|moduleId|startDate|dueDate|estimatedHours|actualHours|attachments|" +
            "requirementNo|isDraft|nodeStatus|workflowInstanceId|creatorId|createdAt|updatedAt|deletedAt)$",
            Pattern.CASE_INSENSITIVE
    );

    private final CustomFieldMapper customFieldMapper;
    private final CustomFieldValueMapper customFieldValueMapper;
    private final CustomFieldMultiValueMapper customFieldMultiValueMapper;
    private final com.demand.system.module.requirement.service.RequirementFieldService requirementFieldService;

    public CustomFieldConfigServiceImpl(CustomFieldMapper customFieldMapper,
                                       CustomFieldValueMapper customFieldValueMapper,
                                       CustomFieldMultiValueMapper customFieldMultiValueMapper,
                                       com.demand.system.module.requirement.service.RequirementFieldService requirementFieldService) {
        this.customFieldMapper = customFieldMapper;
        this.customFieldValueMapper = customFieldValueMapper;
        this.customFieldMultiValueMapper = customFieldMultiValueMapper;
        this.requirementFieldService = requirementFieldService;
    }

    @Override
    public Result<List<CustomFieldConfigDTO>> listFields(String typeCode) {
        // 配置视图返回全部启用+停用字段（停用字段需要能被重新启用）；
        // 软删除字段由 @TableLogic 自动排除。
        LambdaQueryWrapper<CustomField> wrapper = new LambdaQueryWrapper<CustomField>()
                .orderByAsc(CustomField::getSortOrder)
                .orderByAsc(CustomField::getId);
        if (typeCode != null && !typeCode.isBlank()) {
            // 类型编码为 NULL 表示全类型通用字段
            wrapper.and(w -> w.isNull(CustomField::getRequirementTypeCode)
                    .or().eq(CustomField::getRequirementTypeCode, typeCode));
        }
        List<CustomField> fields = customFieldMapper.selectList(wrapper);
        List<CustomFieldConfigDTO> dtos = new ArrayList<>();
        for (CustomField f : fields) {
            dtos.add(toConfigDTO(f));
        }
        return Result.success(dtos);
    }

    @Override
    public Result<List<CustomFieldConfigDTO>> buildCreateSchema(String typeCode) {
        List<CustomField> fields = requirementFieldService.listEnabledFields(typeCode);
        if (fields.isEmpty()) {
            return Result.success(java.util.Collections.emptyList());
        }
        var permission = requirementFieldService.resolveCreatePermission(typeCode);
        List<CustomFieldConfigDTO> schema = requirementFieldService.buildSchema(fields, permission, null);
        // 创建态回填字段默认值，避免必填项从空开始
        for (CustomFieldConfigDTO dto : schema) {
            if (dto.getDefaultValue() != null && !dto.getDefaultValue().isBlank()) {
                applyDefaultValue(dto);
            }
        }
        return Result.success(schema);
    }

    private void applyDefaultValue(CustomFieldConfigDTO dto) {
        String raw = dto.getDefaultValue().trim();
        String type = dto.getFieldType() == null ? "TEXT" : dto.getFieldType().trim().toUpperCase();
        try {
            switch (type) {
                case "NUMBER" -> dto.setValueNumber(new java.math.BigDecimal(raw));
                case "DATE" -> dto.setValueDate(java.time.LocalDate.parse(raw));
                case "BOOLEAN" -> dto.setValueBoolean(Boolean.parseBoolean(raw));
                case "USER" -> dto.setValueUserId(Long.valueOf(raw));
                case "MULTI_SELECT", "MULTI_USER", "FILE" ->
                        dto.setValues(java.util.Arrays.stream(raw.split("[,，]"))
                                .map(String::trim).filter(s -> !s.isEmpty()).collect(java.util.stream.Collectors.toList()));
                default -> dto.setValue(raw);
            }
        } catch (Exception ignore) {
            dto.setValue(raw);
        }
    }

    @Override
    @Transactional
    public Result<Void> createField(CustomField field) {
        if (!StringUtils.hasText(field.getFieldCode())) {
            return Result.fail("字段编码不能为空");
        }
        if (!StringUtils.hasText(field.getName())) {
            return Result.fail("字段名称不能为空");
        }
        String code = field.getFieldCode().trim();
        if (code.length() > 64) {
            return Result.fail("字段编码长度不能超过64字符");
        }
        if (!code.matches("^[a-zA-Z][a-zA-Z0-9_]*$")) {
            return Result.fail("字段编码只能包含字母、数字、下划线，且以字母开头");
        }
        if (SYSTEM_FIELD_CODES.matcher(code).matches()) {
            return Result.fail("字段编码与系统保留字段冲突: " + code);
        }
        if (field.getFieldType() == null || !ALLOWED_TYPES.contains(field.getFieldType().toUpperCase())) {
            return Result.fail("不支持的字段类型: " + field.getFieldType() + "，允许：" + String.join(", ", ALLOWED_TYPES));
        }
        field.setFieldType(field.getFieldType().trim().toUpperCase());
        // 编码唯一性（同类型 + 同编码；类型为 NULL 表示全类型通用字段）
        LambdaQueryWrapper<CustomField> dupWrapper = new LambdaQueryWrapper<CustomField>()
                .eq(CustomField::getFieldCode, code);
        if (field.getRequirementTypeCode() != null) {
            dupWrapper.and(w -> w
                    .eq(CustomField::getRequirementTypeCode, field.getRequirementTypeCode())
                    .or().isNull(CustomField::getRequirementTypeCode));
        }
        if (customFieldMapper.selectCount(dupWrapper) > 0) {
            return Result.fail("字段编码已存在: " + code);
        }
        field.setFieldCode(code);
        // 选项统一归一化为 [{key,label}] 存储；选项字段必须至少有一个选项
        if (isOptionFieldType(field.getFieldType())) {
            String normalized = requirementFieldService.normalizeOptionsJson(field.getOptions());
            if (normalized == null) {
                return Result.fail("选项类型字段至少需要配置一个选项");
            }
            field.setOptions(normalized);
        }
        if (field.getEnabled() == null) {
            field.setEnabled(1);
        }
        if (field.getRequired() == null) {
            field.setRequired(0);
        }
        customFieldMapper.insert(field);
        return Result.success();
    }

    @Override
    @Transactional
    public Result<Void> updateField(CustomField field) {
        if (field.getId() == null) {
            return Result.fail("字段ID不能为空");
        }
        CustomField existing = customFieldMapper.selectById(field.getId());
        if (existing == null) {
            return Result.fail("字段不存在");
        }
        // 守卫一：字段编码是值数据与节点权限引用的稳定契约，创建后禁止修改
        if (StringUtils.hasText(field.getFieldCode())
                && !field.getFieldCode().trim().equals(existing.getFieldCode())) {
            return Result.fail("字段编码创建后不可修改: " + existing.getFieldCode());
        }
        // 守卫二：已有填写数据的字段禁止变更类型，避免历史值失去类型语义
        if (StringUtils.hasText(field.getFieldType())) {
            String newType = field.getFieldType().trim().toUpperCase();
            if (!ALLOWED_TYPES.contains(newType)) {
                return Result.fail("不支持的字段类型: " + field.getFieldType() + "，允许：" + String.join(", ", ALLOWED_TYPES));
            }
            if (!newType.equalsIgnoreCase(existing.getFieldType()) && hasAnyValue(existing.getId())) {
                return Result.fail("字段已存在填写数据，禁止变更类型；请停用该字段并新建字段");
            }
            existing.setFieldType(newType);
        }
        // 允许修改展示名、选项、必填、默认值、排序、启用状态
        if (StringUtils.hasText(field.getName())) {
            existing.setName(field.getName());
        }
        if (field.getOptions() != null) {
            String normalized = requirementFieldService.normalizeOptionsJson(field.getOptions());
            if (isOptionFieldType(existing.getFieldType())) {
                if (normalized == null) {
                    return Result.fail("选项类型字段至少需要配置一个选项");
                }
                // 守卫三：仍被填写数据引用的选项不可删除（key 是值的稳定标识，改名不受限）
                String inUseKey = firstRemovedOptionInUse(existing, normalized);
                if (inUseKey != null) {
                    return Result.fail("选项仍有填写数据引用，不可删除: " + inUseKey + "；可先修改其名称");
                }
            }
            existing.setOptions(normalized);
        }
        if (field.getRequired() != null) {
            existing.setRequired(field.getRequired());
        }
        if (field.getDefaultValue() != null) {
            existing.setDefaultValue(field.getDefaultValue());
        }
        if (field.getSortOrder() != null) {
            existing.setSortOrder(field.getSortOrder());
        }
        if (field.getEnabled() != null) {
            existing.setEnabled(field.getEnabled());
        }
        customFieldMapper.updateById(existing);
        return Result.success();
    }

    private boolean isOptionFieldType(String fieldType) {
        return "SELECT".equalsIgnoreCase(fieldType) || "MULTI_SELECT".equalsIgnoreCase(fieldType);
    }

    /** 字段是否已有任何填写数据（单值/多值表）。 */
    private boolean hasAnyValue(Long fieldId) {
        Long single = customFieldValueMapper.selectCount(new LambdaQueryWrapper<CustomFieldValue>()
                .eq(CustomFieldValue::getFieldId, fieldId));
        if (single != null && single > 0) {
            return true;
        }
        Long multi = customFieldMultiValueMapper.selectCount(new LambdaQueryWrapper<CustomFieldMultiValue>()
                .eq(CustomFieldMultiValue::getFieldId, fieldId));
        return multi != null && multi > 0;
    }

    /** 返回在新选项中被删除、且仍被填写数据引用的选项 key；无则返回 null。 */
    private String firstRemovedOptionInUse(CustomField existing, String newOptionsJson) {
        Set<String> oldKeys = requirementFieldService.parseFieldOptions(existing.getOptions()).stream()
                .map(FieldOption::getKey)
                .collect(Collectors.toSet());
        Set<String> newKeys = requirementFieldService.parseFieldOptions(newOptionsJson).stream()
                .map(FieldOption::getKey)
                .collect(Collectors.toSet());
        if (oldKeys.isEmpty() || newKeys.containsAll(oldKeys)) {
            return null;
        }
        List<String> removed = oldKeys.stream().filter(k -> !newKeys.contains(k)).toList();
        if (removed.isEmpty()) {
            return null;
        }
        Long single = customFieldValueMapper.selectCount(new LambdaQueryWrapper<CustomFieldValue>()
                .eq(CustomFieldValue::getFieldId, existing.getId())
                .in(CustomFieldValue::getValueText, removed));
        if (single != null && single > 0) {
            return removed.get(0);
        }
        Long multi = customFieldMultiValueMapper.selectCount(new LambdaQueryWrapper<CustomFieldMultiValue>()
                .eq(CustomFieldMultiValue::getFieldId, existing.getId())
                .in(CustomFieldMultiValue::getValueText, removed));
        return multi != null && multi > 0 ? removed.get(0) : null;
    }

    @Override
    @Transactional
    public Result<Void> deleteField(Long id) {
        if (id == null) {
            return Result.fail("字段ID不能为空");
        }
        CustomField existing = customFieldMapper.selectById(id);
        if (existing == null) {
            return Result.fail("字段不存在");
        }
        // 软删除：逻辑删除字段由 @TableLogic(delval = "NOW()") 通过 deleteById 生成
        customFieldMapper.deleteById(id);
        return Result.success();
    }

    @Override
    @Transactional
    public Result<List<CustomField>> sortFields(List<SortItemDTO> items) {
        if (items == null || items.isEmpty()) {
            return Result.fail("排序列表不能为空");
        }
        for (SortItemDTO item : items) {
            if (item == null || item.getId() == null) {
                continue;
            }
            customFieldMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<CustomField>()
                    .eq(CustomField::getId, item.getId())
                    .set(CustomField::getSortOrder, item.getSortOrder() == null ? 0 : item.getSortOrder()));
        }
        List<CustomField> all = customFieldMapper.selectList(
                new LambdaQueryWrapper<CustomField>()
                        .orderByAsc(CustomField::getSortOrder)
                        .orderByAsc(CustomField::getId));
        return Result.success(all);
    }

    private CustomFieldConfigDTO toConfigDTO(CustomField f) {
        CustomFieldConfigDTO dto = new CustomFieldConfigDTO();
        dto.setId(f.getId());
        dto.setFieldCode(f.getFieldCode());
        dto.setRequirementTypeCode(f.getRequirementTypeCode());
        dto.setName(f.getName());
        dto.setFieldType(f.getFieldType());
        dto.setOptions(f.getOptions());
        dto.setOptionList(requirementFieldService.parseFieldOptions(f.getOptions()));
        dto.setRequired(Integer.valueOf(1).equals(f.getRequired()));
        dto.setDefaultValue(f.getDefaultValue());
        dto.setSortOrder(f.getSortOrder());
        dto.setEnabled(Integer.valueOf(1).equals(f.getEnabled()));
        return dto;
    }
}
