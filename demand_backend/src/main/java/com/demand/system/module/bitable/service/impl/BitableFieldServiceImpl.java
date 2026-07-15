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
import org.springframework.beans.BeanUtils;
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

    public BitableFieldServiceImpl(BitableFieldMapper fieldMapper,
                                   BitableCellMapper cellMapper,
                                   BitableConverter converter) {
        this.fieldMapper = fieldMapper;
        this.cellMapper = cellMapper;
        this.converter = converter;
    }

    @Override
    public List<BitableFieldVO> listFields(Long tableId) {
        List<BitableField> fields = fieldMapper.selectByTableId(tableId);
        return converter.toFieldVOList(fields);
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
        BeanUtils.copyProperties(dto, field);
        field.setTableId(tableId);

        // 设置 sortOrder: 当前最大 + 1
        Integer maxOrder = fieldMapper.selectMaxSortOrderByTableId(tableId);
        field.setSortOrder(maxOrder != null ? maxOrder + 1 : 1);

        fieldMapper.insert(field);
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
            wrapper.set("config", dto.getConfig());
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

        // 软删字段（MyBatis-Plus @TableLogic 自动设置 deleted_at=1）
        fieldMapper.deleteById(id);
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
}