package com.demand.system.module.bitable.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.bitable.constant.FieldType;
import com.demand.system.module.bitable.converter.BitableConverter;
import com.demand.system.module.bitable.dto.BitableFieldCreateDTO;
import com.demand.system.module.bitable.dto.BitableFieldUpdateDTO;
import com.demand.system.module.bitable.dto.BitableFieldVO;
import com.demand.system.module.bitable.entity.BitableField;
import com.demand.system.module.bitable.mapper.BitableFieldMapper;
import com.demand.system.module.bitable.mapper.BitableCellMapper;
import com.demand.system.module.bitable.service.BitableFieldService;
import com.demand.system.module.bitable.service.BitableFieldPermissionService;
import com.demand.system.module.bitable.service.BitableFormulaDependencyService;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.bitable.util.BitableAuditHelper;
import com.demand.system.module.bitable.util.BitableJsonUtils;
import com.demand.system.module.bitable.constant.OperationType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 多维表格-字段定义 Service 实现
 */
@Service
public class BitableFieldServiceImpl implements BitableFieldService {

    private final BitableFieldMapper fieldMapper;
    private final BitableCellMapper cellMapper;
    private final BitableConverter converter;
    private final BitableFormulaDependencyService formulaDependencyService;
    private final BitableAuditHelper auditHelper;
    private final BitableFieldPermissionService fieldPermissionService;

    public BitableFieldServiceImpl(BitableFieldMapper fieldMapper,
                                   BitableCellMapper cellMapper,
                                   BitableConverter converter,
                                   BitableFormulaDependencyService formulaDependencyService,
                                   BitableAuditHelper auditHelper,
                                   BitableFieldPermissionService fieldPermissionService) {
        this.fieldMapper = fieldMapper;
        this.cellMapper = cellMapper;
        this.converter = converter;
        this.formulaDependencyService = formulaDependencyService;
        this.auditHelper = auditHelper;
        this.fieldPermissionService = fieldPermissionService;
    }

    @Override
    public List<BitableFieldVO> listFields(Long tableId) {
        List<BitableField> fields = fieldMapper.selectByTableId(tableId);
        // 按当前用户角色解析字段级权限（readonly / hidden），前端据此渲染只读或隐藏列
        Map<Long, String> permissionMap = fieldPermissionService.resolveFieldPermissions(
                tableId, SecurityUtils.getCurrentUserId());
        List<BitableFieldVO> result = new ArrayList<>();
        for (BitableField field : fields) {
            BitableFieldVO vo = toFieldVO(field);
            vo.setPermission(permissionMap.getOrDefault(field.getId(), "editable"));
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createField(Long tableId, BitableFieldCreateDTO dto, Long userId) {
        // 验证 fieldType 是合法枚举值
        FieldType fieldType = FieldType.fromCode(dto.getFieldType());
        if (fieldType == null) {
            throw new BusinessException("不支持的字段类型: " + dto.getFieldType());
        }
        // 字段名称：非空 + 长度 + 表内唯一
        String fieldName = validateFieldName(tableId, dto.getName(), null);

        BitableField field = new BitableField();
        field.setName(fieldName);
        field.setFieldType(dto.getFieldType());
        field.setConfig(BitableJsonUtils.toJsonString(dto.getConfig()));
        field.setRequired(dto.getRequired());
        field.setAiPrompt(dto.getAiPrompt());
        field.setIsAiField(dto.getIsAiField());
        field.setWidth(dto.getWidth());
        field.setDescription(dto.getDescription());
        field.setTableId(tableId);

        // 设置 sortOrder: 当前最大 + 1
        Integer maxOrder = fieldMapper.selectMaxSortOrderByTableId(tableId);
        field.setSortOrder(maxOrder != null ? maxOrder + 1 : 1);

        fieldMapper.insert(field);

        // AI 字段：自动设置 is_ai_field
        if (fieldType != null && fieldType.isAiType()) {
            fieldMapper.update(null, new UpdateWrapper<BitableField>()
                    .eq("id", field.getId())
                    .set("is_ai_field", 1));
        }

        // 公式字段：解析依赖并检测循环引用
        if ("formula".equals(dto.getFieldType()) && dto.getConfig() != null) {
            String formulaExpr = extractFormulaExpr(dto.getConfig());
            if (formulaExpr != null) {
                formulaDependencyService.updateDependencies(field.getId(), formulaExpr);
                List<Long> cycle = formulaDependencyService.detectCycle(field.getId());
                if (cycle != null) {
                    // 回滚：删除刚插入的字段和依赖
                    formulaDependencyService.deleteDependencies(field.getId());
                    throw new BusinessException("公式存在循环引用: " + cycle);
                }
            }
        }

        // 审计
        auditHelper.recordByTable(tableId, userId, OperationType.ADD_FIELD,
                "{\"fieldId\":" + field.getId() + ",\"name\":\"" + fieldName + "\",\"fieldType\":\"" + dto.getFieldType() + "\"}");

        return field.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateField(Long id, BitableFieldUpdateDTO dto, Long userId) {        BitableField existing = fieldMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("字段不存在");
        }

        // 验证 fieldType（如果传了新的 fieldType）
        if (dto.getFieldType() != null) {
            FieldType fieldType = FieldType.fromCode(dto.getFieldType());
            if (fieldType == null) {
                throw new BusinessException("不支持的字段类型: " + dto.getFieldType());
            }
        }

        // 选项定义管理权限：角色被限为「仅可新增」时，不能改动/删除已有选项
        if (dto.getConfig() != null) {
            String effectiveType = dto.getFieldType() != null ? dto.getFieldType() : existing.getFieldType();
            if ("single_select".equals(effectiveType) || "multi_select".equals(effectiveType)) {
                fieldPermissionService.checkFieldOptionManage(id,
                        extractOptionLabels(existing.getConfig()),
                        extractOptionLabels(BitableJsonUtils.toJsonString(dto.getConfig())),
                        userId);
            }
        }

        UpdateWrapper<BitableField> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id);
        if (dto.getName() != null) {
            // 字段名称：非空 + 长度 + 表内唯一（排除自身）
            wrapper.set("name", validateFieldName(existing.getTableId(), dto.getName(), id));
        }
        if (dto.getFieldType() != null) {
            wrapper.set("field_type", dto.getFieldType());
        }
        if (dto.getConfig() != null) {
            wrapper.set("config", BitableJsonUtils.toJsonString(dto.getConfig()));
        }
        if (dto.getRequired() != null) {
            wrapper.set("required", dto.getRequired());
        }
        if (dto.getAiPrompt() != null) {
            wrapper.set("ai_prompt", dto.getAiPrompt());
        }
        if (dto.getIsAiField() != null) {
            wrapper.set("is_ai_field", dto.getIsAiField());
        }
        if (dto.getWidth() != null) {
            wrapper.set("width", dto.getWidth());
        }
        if (dto.getDescription() != null) {
            wrapper.set("description", dto.getDescription());
        }
        fieldMapper.update(null, wrapper);

        // 审计
        auditHelper.recordByTable(existing.getTableId(), userId, OperationType.UPDATE_FIELD,
                "{\"fieldId\":" + id + "}");

        // 公式字段更新时：重新解析依赖并检测循环引用
        String effectiveFieldType = dto.getFieldType() != null ? dto.getFieldType() : existing.getFieldType();
        if ("formula".equals(effectiveFieldType)) {
            // 获取更新后的公式表达式
            String formulaExpr = null;
            if (dto.getConfig() != null) {
                formulaExpr = extractFormulaExpr(dto.getConfig());
            } else if (existing.getConfig() != null) {
                formulaExpr = extractFormulaExprFromJson(existing.getConfig());
            }

            if (formulaExpr != null) {
                formulaDependencyService.updateDependencies(id, formulaExpr);
                List<Long> cycle = formulaDependencyService.detectCycle(id);
                if (cycle != null) {
                    // 回滚依赖更新
                    formulaDependencyService.deleteDependencies(id);
                    throw new BusinessException("公式存在循环引用: " + cycle);
                }
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteField(Long id, Long userId) {
        BitableField existing = fieldMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("字段不存在");
        }

        // 物理删除该字段对应的 cell_values（字段没了，cell_values 也不再有意义）
        LambdaQueryWrapper<com.demand.system.module.bitable.entity.BitableCellValue> cellWrapper =
                new LambdaQueryWrapper<>();
        cellWrapper.eq(com.demand.system.module.bitable.entity.BitableCellValue::getFieldId, id);
        cellMapper.delete(cellWrapper);

        // 清理公式依赖关系
        formulaDependencyService.deleteDependencies(id);

        // 清理字段级权限（表上无外键，不显式删就会留下指向已删字段的孤儿行）
        fieldPermissionService.deleteByFieldId(id);

        // 软删字段（MyBatis-Plus @TableLogic 自动设置 deleted_at=1）
        fieldMapper.deleteById(id);

        // 审计
        auditHelper.recordByTable(existing.getTableId(), userId, OperationType.DELETE_FIELD,
                "{\"fieldId\":" + id + ",\"name\":\"" + existing.getName() + "\"}");
    }

    /** 字段名称长度上限（与前端 FieldAttributeForm 的 maxlength 保持一致）。 */
    private static final int FIELD_NAME_MAX_LENGTH = 50;

    /**
     * 校验字段名称并返回去除首尾空白后的结果。
     * <p>
     * 规则：非空、长度 ≤ {@value #FIELD_NAME_MAX_LENGTH}、同表内不重名（大小写不敏感，
     * 与数据库 utf8mb4_0900_ai_ci 的排序规则一致）。
     *
     * @param excludeFieldId 更新场景下需要排除的自身字段 ID，新建时传 {@code null}
     */
    private String validateFieldName(Long tableId, String rawName, Long excludeFieldId) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) {
            throw new BusinessException("字段名称不能为空");
        }
        if (name.length() > FIELD_NAME_MAX_LENGTH) {
            throw new BusinessException("字段名称不能超过 " + FIELD_NAME_MAX_LENGTH + " 个字符");
        }
        List<BitableField> siblings = fieldMapper.selectByTableId(tableId);
        for (BitableField sibling : siblings) {
            if (excludeFieldId != null && excludeFieldId.equals(sibling.getId())) {
                continue;
            }
            if (name.equalsIgnoreCase(sibling.getName())) {
                throw new BusinessException("字段名称「" + name + "」已存在");
            }
        }
        return name;
    }

    private BitableFieldVO toFieldVO(BitableField field) {
        BitableFieldVO vo = new BitableFieldVO();
        vo.setId(field.getId());
        vo.setTableId(field.getTableId());
        vo.setName(field.getName());
        vo.setFieldType(field.getFieldType());
        vo.setConfig(BitableJsonUtils.parseJson(field.getConfig()));
        vo.setRequired(field.getRequired());
        vo.setAiPrompt(field.getAiPrompt());
        vo.setIsAiField(field.getIsAiField());
        vo.setSortOrder(field.getSortOrder());
        vo.setWidth(field.getWidth());
        vo.setDescription(field.getDescription());
        vo.setCreatedAt(field.getCreatedAt());
        vo.setUpdatedAt(field.getUpdatedAt());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sortFields(Long tableId, List<Long> fieldIds) {
        // 校验所有字段确实属于当前表，防止跨表篡改其他 Base 的字段排序
        List<BitableField> fields = fieldMapper.selectBatchIds(fieldIds);
        if (fields.size() != fieldIds.stream().distinct().count()) {
            throw new BusinessException("存在无效的字段ID");
        }
        for (BitableField field : fields) {
            if (!tableId.equals(field.getTableId())) {
                throw new BusinessException("字段不属于当前数据表");
            }
        }

        List<Map<String, Object>> sortList = new ArrayList<>();
        for (int i = 0; i < fieldIds.size(); i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", fieldIds.get(i));
            item.put("sortOrder", i + 1);
            sortList.add(item);
        }
        fieldMapper.batchUpdateSortOrder(sortList);
    }

    /**
     * 从 DTO 的 config 对象中提取公式表达式
     * config 可能是 Map 或 JSON 字符串
     */
    @SuppressWarnings("unchecked")
    private String extractFormulaExpr(Object config) {
        if (config == null) {
            return null;
        }
        if (config instanceof Map) {
            Object expr = ((Map<String, Object>) config).get("formula");
            return expr != null ? expr.toString() : null;
        }
        // 如果是字符串，先解析为 JSON 再提取
        return extractFormulaExprFromJson(BitableJsonUtils.toJsonString(config));
    }

    /**
     * 从 JSON 字符串中提取公式表达式
     */
    @SuppressWarnings("unchecked")
    private String extractFormulaExprFromJson(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return null;
        }
        Object parsed = BitableJsonUtils.parseJson(configJson);
        if (parsed instanceof Map) {
            Object expr = ((Map<String, Object>) parsed).get("formula");
            return expr != null ? expr.toString() : null;
        }
        return null;
    }

    /** 从字段 config JSON 中提取单选/多选选项 label 集合（config.options[].label） */
    private java.util.Set<String> extractOptionLabels(String configJson) {
        java.util.Set<String> labels = new java.util.HashSet<>();
        if (configJson == null || configJson.isBlank()) {
            return labels;
        }
        try {
            Object parsed = BitableJsonUtils.parseJson(configJson);
            if (parsed instanceof Map<?, ?> map && map.get("options") instanceof List<?> options) {
                for (Object option : options) {
                    if (option instanceof Map<?, ?> opt && opt.get("label") != null) {
                        labels.add(String.valueOf(opt.get("label")));
                    }
                }
            }
        } catch (Exception ignored) {
            // 解析失败按空集合处理，交由权限校验与后续保存逻辑兜底
        }
        return labels;
    }
}