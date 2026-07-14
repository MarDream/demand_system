package com.demand.system.module.bitable.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.util.UserNameResolver;
import com.demand.system.module.bitable.converter.BitableConverter;
import com.demand.system.module.bitable.dto.BitableCellValueVO;
import com.demand.system.module.bitable.dto.BitableRecordVO;
import com.demand.system.module.bitable.entity.BitableCellValue;
import com.demand.system.module.bitable.entity.BitableField;
import com.demand.system.module.bitable.entity.BitableRecord;
import com.demand.system.module.bitable.mapper.BitableCellMapper;
import com.demand.system.module.bitable.mapper.BitableFieldMapper;
import com.demand.system.module.bitable.mapper.BitableRecordMapper;
import com.demand.system.module.bitable.service.BitableLinkService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 多维表格-关联字段 Service 实现
 * 数据存储: 使用 BitableCellValue.valueJson 存储关联记录ID数组
 */
@Service
public class BitableLinkServiceImpl implements BitableLinkService {

    private final BitableFieldMapper fieldMapper;
    private final BitableRecordMapper recordMapper;
    private final BitableCellMapper cellMapper;
    private final BitableConverter converter;
    private final UserNameResolver userNameResolver;
    private final ObjectMapper objectMapper;

    public BitableLinkServiceImpl(BitableFieldMapper fieldMapper,
                                  BitableRecordMapper recordMapper,
                                  BitableCellMapper cellMapper,
                                  BitableConverter converter,
                                  UserNameResolver userNameResolver,
                                  ObjectMapper objectMapper) {
        this.fieldMapper = fieldMapper;
        this.recordMapper = recordMapper;
        this.cellMapper = cellMapper;
        this.converter = converter;
        this.userNameResolver = userNameResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<BitableRecordVO> listLinkableRecords(Long targetTableId, String keyword, Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            pageSize = 50;
        }
        int limit = Math.min(pageSize, 200);

        // 查询目标表的记录
        List<BitableRecord> records;
        if (keyword != null && !keyword.isBlank()) {
            // 简单实现：按关键词在前 200 条中过滤
            List<BitableRecord> all = recordMapper.selectByTableId(targetTableId, 0, 200);
            records = all.stream()
                    .filter(r -> matchesKeyword(r, keyword))
                    .limit(limit)
                    .collect(Collectors.toList());
        } else {
            records = recordMapper.selectByTableId(targetTableId, 0, limit);
        }

        List<BitableRecordVO> voList = converter.toRecordVOList(records);

        // 批量查询 cells
        if (!records.isEmpty()) {
            List<Long> recordIds = records.stream().map(BitableRecord::getId).collect(Collectors.toList());
            List<BitableCellValue> cells = cellMapper.selectByRecordIds(recordIds);
            List<BitableCellValueVO> cellVOs = converter.toCellValueVOList(cells);

            Map<Long, Map<Long, BitableCellValueVO>> recordCellsMap = new java.util.LinkedHashMap<>();
            for (BitableCellValueVO cellVO : cellVOs) {
                recordCellsMap.computeIfAbsent(cellVO.getRecordId(), k -> new java.util.LinkedHashMap<>())
                        .put(cellVO.getFieldId(), cellVO);
            }

            for (BitableRecordVO vo : voList) {
                vo.setCells(recordCellsMap.getOrDefault(vo.getId(), new java.util.LinkedHashMap<>()));
                vo.setCreatedByName(userNameResolver.resolveUserName(vo.getCreatedBy(), "未知用户"));
                vo.setUpdatedByName(userNameResolver.resolveUserName(vo.getUpdatedBy(), "未知用户"));
            }
        }

        return voList;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void linkRecords(Long fieldId, Long recordId, List<Long> targetRecordIds, Long userId) {
        // 验证字段存在且为 link 类型
        BitableField field = fieldMapper.selectById(fieldId);
        if (field == null) {
            throw new BusinessException("字段不存在");
        }
        if (!"link".equals(field.getFieldType())) {
            throw new BusinessException("字段不是关联类型");
        }

        // 验证当前记录存在
        BitableRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException("记录不存在");
        }

        // 将目标记录ID列表转为 JSON 数组字符串存储到 valueJson
        String valueJson;
        try {
            valueJson = objectMapper.writeValueAsString(targetRecordIds != null ? targetRecordIds : Collections.emptyList());
        } catch (Exception e) {
            throw new BusinessException("序列化关联记录ID失败: " + e.getMessage());
        }

        // 保存或更新单元格值
        BitableCellValue cell = new BitableCellValue();
        cell.setRecordId(recordId);
        cell.setFieldId(fieldId);
        cell.setValueJson(valueJson);
        cellMapper.saveOrUpdateCell(cell);
    }

    @Override
    public List<Long> getLinkedRecordIds(Long fieldId, Long recordId) {
        BitableCellValue cell = cellMapper.selectByRecordAndField(recordId, fieldId);
        if (cell == null || cell.getValueJson() == null || cell.getValueJson().isBlank()) {
            return Collections.emptyList();
        }

        try {
            List<Long> ids = objectMapper.readValue(cell.getValueJson(), new TypeReference<List<Long>>() {});
            return ids != null ? ids : Collections.emptyList();
        } catch (Exception e) {
            // 如果解析失败，尝试兼容旧格式
            return Collections.emptyList();
        }
    }

    /**
     * 简单的关键词匹配（基于记录的 sortOrder 和创建者名称）
     */
    private boolean matchesKeyword(BitableRecord record, String keyword) {
        String lowerKeyword = keyword.toLowerCase();
        String createdByName = userNameResolver.resolveUserName(record.getCreatedBy(), "");
        return createdByName != null && createdByName.toLowerCase().contains(lowerKeyword);
    }
}
