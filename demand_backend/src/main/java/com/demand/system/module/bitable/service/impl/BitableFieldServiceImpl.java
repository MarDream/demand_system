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
import com.demand.system.module.bitable.service.BitableFormulaDependencyService;
import com.demand.system.module.bitable.util.BitableJsonUtils;
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

    public BitableFieldServiceImpl(BitableFieldMapper fieldMapper,
                                   BitableCellMapper cellMapper,
                                   BitableConverter converter,
                                   BitableFormulaDependencyService formulaDependencyService) {
        this.fieldMapper = fieldMapper;
        this.cellMapper = cellMapper;
        this.converter = converter;
        this.formulaDependencyService = formulaDependencyService;
    }

    @Override
    public List<BitableFieldVO> listFields(Long tableId) {
        List<BitableField> fields = fieldMapper.selectByTableId(tableId);
        List<BitableFieldVO> result = new ArrayList<>();
        for (BitableField field : fields) {
            result.add(toFieldVO(field));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createField(Long tableId, BitableFieldCreateDTO dto) {
        // 验证 fieldType 是合法枚举值
        FieldType fieldType = FieldType.fromCode(dto.getFieldType());
        if (fieldType == null) {
            throw new BusinessException("不支持的字段类型: " + dto.getFieldType());
        }

        BitableField field = new BitableField();
        field.setName(dto.getName());
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

        return field.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateField(Long id, BitableFieldUpdateDTO dto) {
        BitableField existing = fieldMapper.selectById(id);
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

        UpdateWrapper<BitableField> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id);
        if (dto.getName() != null) {
            wrapper.set("name", dto.getName());
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
    public void deleteField(Long id) {
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

        // 软删字段（MyBatis-Plus @TableLogic 自动设置 deleted_at=1）
        fieldMapper.deleteById(id);
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
}