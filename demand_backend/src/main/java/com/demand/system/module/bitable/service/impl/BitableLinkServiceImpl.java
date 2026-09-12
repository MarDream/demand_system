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
import com.demand.system.module.bitable.util.BitableJsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /** 关联记录搜索的最大扫描行数 */
    private static final int LINK_SEARCH_SCAN_LIMIT = 2000;
    private static final int LINK_SEARCH_PAGE_SIZE = 500;

    @Override
    public List<BitableRecordVO> listLinkableRecords(Long targetTableId, String keyword, Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            pageSize = 50;
        }
        int limit = Math.min(pageSize, 200);

        // 查询目标表的记录
        List<BitableRecord> records;
        if (keyword != null && !keyword.isBlank()) {
            records = searchRecordsByKeyword(targetTableId, keyword, limit);
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
        if (!("link".equals(field.getFieldType()) || "bidirectional_link".equals(field.getFieldType()))) {
            throw new BusinessException("字段不是关联类型");
        }

        // 验证当前记录存在且属于字段所在表，防止向其他表的记录写入关联值
        BitableRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException("记录不存在");
        }
        if (!field.getTableId().equals(record.getTableId())) {
            throw new BusinessException("记录不属于该关联字段所在的数据表");
        }

        // 验证目标记录都属于关联字段配置的目标表，防止建立跨 Base 的非法关联
        if (targetRecordIds != null && !targetRecordIds.isEmpty()) {
            Long linkTargetTableId = parseLinkTargetTableId(field);
            if (linkTargetTableId == null) {
                throw new BusinessException("关联字段未配置目标表");
            }
            List<Long> distinctIds = targetRecordIds.stream().distinct().collect(Collectors.toList());
            List<BitableRecord> targetRecords = recordMapper.selectBatchIds(distinctIds);
            if (targetRecords.size() != distinctIds.size()) {
                throw new BusinessException("部分关联记录不存在");
            }
            for (BitableRecord targetRecord : targetRecords) {
                if (!linkTargetTableId.equals(targetRecord.getTableId())) {
                    throw new BusinessException("关联记录不属于字段配置的目标表");
                }
            }
        }

        List<Long> oldTargetRecordIds = getLinkedRecordIds(fieldId, recordId);

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

        syncBidirectionalReverseLinks(field, recordId, oldTargetRecordIds, targetRecordIds != null ? targetRecordIds : Collections.emptyList());
    }

    @SuppressWarnings("unchecked")
    private Long parseLinkTargetTableId(BitableField field) {
        Object parsed = BitableJsonUtils.parseJson(field.getConfig());
        if (!(parsed instanceof Map)) {
            return null;
        }
        Object raw = ((Map<String, Object>) parsed).get("linkTargetTableId");
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String str && !str.isBlank()) {
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
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


    private void syncBidirectionalReverseLinks(BitableField field, Long recordId, List<Long> oldTargetIds, List<Long> newTargetIds) {
        if (!"bidirectional_link".equals(field.getFieldType())) {
            return;
        }
        Long reverseFieldId = parseReverseFieldId(field);
        if (reverseFieldId == null) {
            return;
        }
        Set<Long> oldSet = new LinkedHashSet<>(oldTargetIds == null ? Collections.emptyList() : oldTargetIds);
        Set<Long> newSet = new LinkedHashSet<>(newTargetIds == null ? Collections.emptyList() : newTargetIds);

        for (Long targetId : newSet) {
            List<Long> reverseIds = new ArrayList<>(getLinkedRecordIds(reverseFieldId, targetId));
            if (!reverseIds.contains(recordId)) {
                reverseIds.add(recordId);
                saveLinkCell(reverseFieldId, targetId, reverseIds);
            }
        }
        for (Long removedTargetId : oldSet) {
            if (newSet.contains(removedTargetId)) {
                continue;
            }
            List<Long> reverseIds = new ArrayList<>(getLinkedRecordIds(reverseFieldId, removedTargetId));
            if (reverseIds.remove(recordId)) {
                saveLinkCell(reverseFieldId, removedTargetId, reverseIds);
            }
        }
    }

    private void saveLinkCell(Long fieldId, Long recordId, List<Long> targetRecordIds) {
        BitableCellValue cell = new BitableCellValue();
        cell.setRecordId(recordId);
        cell.setFieldId(fieldId);
        cell.setValueJson(BitableJsonUtils.toJsonString(targetRecordIds));
        cellMapper.saveOrUpdateCell(cell);
    }

    @SuppressWarnings("unchecked")
    private Long parseReverseFieldId(BitableField field) {
        Object parsed = BitableJsonUtils.parseJson(field.getConfig());
        if (!(parsed instanceof Map)) {
            return null;
        }
        Map<String, Object> config = (Map<String, Object>) parsed;
        Object raw = config.get("reverseFieldId");
        if (raw == null) {
            raw = config.get("bidirectionalFieldId");
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String str && !str.isBlank()) {
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 关键词搜索：分页扫描最多 2000 行，匹配首个 text 字段的值或创建人姓名（原先仅扫描前 200 条且只匹配创建人）
     */
    private List<BitableRecord> searchRecordsByKeyword(Long targetTableId, String keyword, int limit) {
        String lowerKeyword = keyword.toLowerCase();

        // 找到目标表第一个 text 字段，用于按内容匹配
        List<BitableField> fields = fieldMapper.selectByTableId(targetTableId);
        Long textFieldId = fields.stream()
                .filter(f -> "text".equals(f.getFieldType()))
                .map(BitableField::getId)
                .findFirst()
                .orElse(null);

        List<BitableRecord> matched = new ArrayList<>();
        int total = recordMapper.countByTableId(targetTableId);
        int scanLimit = Math.min(total, LINK_SEARCH_SCAN_LIMIT);
        Set<Long> seen = new LinkedHashSet<>();

        for (int offset = 0; offset < scanLimit && matched.size() < limit; offset += LINK_SEARCH_PAGE_SIZE) {
            List<BitableRecord> page = recordMapper.selectByTableId(targetTableId, offset, LINK_SEARCH_PAGE_SIZE);
            if (page.isEmpty()) {
                break;
            }
            // 批量取本页 text 字段值
            List<Long> pageIds = page.stream().map(BitableRecord::getId).collect(Collectors.toList());
            Map<Long, String> textByRecord = new HashMap<>();
            if (textFieldId != null) {
                for (BitableCellValue cell : cellMapper.selectByRecordIds(pageIds)) {
                    if (textFieldId.equals(cell.getFieldId()) && cell.getValueText() != null) {
                        textByRecord.putIfAbsent(cell.getRecordId(), cell.getValueText());
                    }
                }
            }
            for (BitableRecord record : page) {
                if (seen.add(record.getId())) {
                    String text = textByRecord.get(record.getId());
                    String creator = userNameResolver.resolveUserName(record.getCreatedBy(), "");
                    boolean hit = (text != null && text.toLowerCase().contains(lowerKeyword))
                            || (creator != null && creator.toLowerCase().contains(lowerKeyword));
                    if (hit) {
                        matched.add(record);
                        if (matched.size() >= limit) {
                            break;
                        }
                    }
                }
            }
        }
        return matched;
    }
}
