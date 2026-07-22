package com.demand.system.module.bitable.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.common.result.PageResult;
import com.demand.system.common.util.UserNameResolver;
import com.demand.system.module.bitable.converter.BitableConverter;
import com.demand.system.module.bitable.dto.BitableCellValueVO;
import com.demand.system.module.bitable.dto.BitableRecordCreateDTO;
import com.demand.system.module.bitable.dto.BitableRecordVO;
import com.demand.system.module.bitable.dto.CellValueDTO;
import com.demand.system.module.bitable.dto.RecordGroupVO;
import com.demand.system.module.bitable.dto.RecordQueryDTO;
import com.demand.system.module.bitable.dto.BitableViewVO;
import com.demand.system.module.bitable.entity.BitableCellValue;
import com.demand.system.module.bitable.entity.BitableComment;
import com.demand.system.module.bitable.entity.BitableField;
import com.demand.system.module.bitable.entity.BitableRecord;
import com.demand.system.module.bitable.entity.BitableTable;
import com.demand.system.module.bitable.mapper.BitableCellMapper;
import com.demand.system.module.bitable.mapper.BitableCommentMapper;
import com.demand.system.module.bitable.mapper.BitableFieldMapper;
import com.demand.system.module.bitable.mapper.BitableRecordMapper;
import com.demand.system.module.bitable.mapper.BitableTableMapper;
import com.demand.system.module.bitable.service.BitableAutomationService;
import com.demand.system.module.bitable.service.BitableCollaborationService;
import com.demand.system.module.bitable.service.BitableFormulaService;
import com.demand.system.module.bitable.service.BitableLinkService;
import com.demand.system.module.bitable.service.BitableRecordService;
import com.demand.system.module.bitable.service.BitableViewService;
import com.demand.system.module.bitable.util.BitableJsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 多维表格-记录行 Service 实现
 */
@Service
public class BitableRecordServiceImpl implements BitableRecordService {

    private static final Logger log = LoggerFactory.getLogger(BitableRecordServiceImpl.class);

    private final BitableRecordMapper recordMapper;
    private final BitableCellMapper cellMapper;
    private final BitableCommentMapper commentMapper;
    private final BitableFieldMapper fieldMapper;
    private final BitableTableMapper tableMapper;
    private final BitableFormulaService formulaService;
    private final BitableLinkService linkService;
    private final BitableViewService viewService;
    private final BitableConverter converter;
    private final UserNameResolver userNameResolver;
    private final BitableAutomationService automationService;
    private final BitableCollaborationService collaborationService;

    public BitableRecordServiceImpl(BitableRecordMapper recordMapper,
                                    BitableCellMapper cellMapper,
                                    BitableCommentMapper commentMapper,
                                    BitableFieldMapper fieldMapper,
                                    BitableTableMapper tableMapper,
                                    BitableFormulaService formulaService,
                                    BitableLinkService linkService,
                                    BitableViewService viewService,
                                    BitableConverter converter,
                                    UserNameResolver userNameResolver,
                                    @Lazy BitableAutomationService automationService,
                                    BitableCollaborationService collaborationService) {
        this.recordMapper = recordMapper;
        this.cellMapper = cellMapper;
        this.commentMapper = commentMapper;
        this.fieldMapper = fieldMapper;
        this.tableMapper = tableMapper;
        this.formulaService = formulaService;
        this.linkService = linkService;
        this.viewService = viewService;
        this.converter = converter;
        this.userNameResolver = userNameResolver;
        this.automationService = automationService;
        this.collaborationService = collaborationService;
    }

    @Override
    public PageResult<BitableRecordVO> listRecords(Long tableId, Integer pageNum, Integer pageSize) {
        int total = recordMapper.countByTableId(tableId);
        int offset = (pageNum - 1) * pageSize;

        List<BitableRecord> records = recordMapper.selectByTableId(tableId, offset, pageSize);
        List<BitableRecordVO> voList = converter.toRecordVOList(records);

        // 批量查询这些 records 的 cell_values
        if (!records.isEmpty()) {
            List<Long> recordIds = records.stream().map(BitableRecord::getId).collect(Collectors.toList());
            List<BitableCellValue> cells = cellMapper.selectByRecordIds(recordIds);
            List<BitableCellValueVO> cellVOs = cells.stream().map(this::toCellValueVO).toList();

            // 组装成 Map<recordId, Map<fieldId, CellValueVO>>
            Map<Long, Map<Long, BitableCellValueVO>> recordCellsMap = new LinkedHashMap<>();
            for (BitableCellValueVO cellVO : cellVOs) {
                recordCellsMap.computeIfAbsent(cellVO.getRecordId(), k -> new LinkedHashMap<>())
                        .put(cellVO.getFieldId(), cellVO);
            }

            List<BitableField> fields = fieldMapper.selectByTableId(tableId);

            // 填充 VO 的 cells、系统/计算字段和 createdByName/updatedByName
            for (BitableRecordVO vo : voList) {
                vo.setCells(recordCellsMap.getOrDefault(vo.getId(), new LinkedHashMap<>()));
                vo.setCreatedByName(userNameResolver.resolveUserName(vo.getCreatedBy(), "未知用户"));
                vo.setUpdatedByName(userNameResolver.resolveUserName(vo.getUpdatedBy(), "未知用户"));
                appendComputedCells(vo, fields);
            }
        }

        return new PageResult<>(voList, total, pageNum, pageSize);
    }

    @Override
    public BitableRecordVO getRecordById(Long id) {
        BitableRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("记录不存在");
        }

        BitableRecordVO vo = converter.toRecordVO(record);

        // 查询该记录的所有单元格值
        List<BitableCellValue> cells = cellMapper.selectByRecordIds(List.of(id));
        Map<Long, BitableCellValueVO> cellsMap = new LinkedHashMap<>();
        for (BitableCellValue cell : cells) {
            BitableCellValueVO cellVO = toCellValueVO(cell);
            cellsMap.put(cellVO.getFieldId(), cellVO);
        }
        vo.setCells(cellsMap);
        vo.setCreatedByName(userNameResolver.resolveUserName(record.getCreatedBy(), "未知用户"));
        vo.setUpdatedByName(userNameResolver.resolveUserName(record.getUpdatedBy(), "未知用户"));
        appendComputedCells(vo, fieldMapper.selectByTableId(record.getTableId()));

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRecord(Long tableId, BitableRecordCreateDTO dto, Long userId) {
        BitableRecord record = new BitableRecord();
        record.setTableId(tableId);
        record.setCreatedBy(userId);
        record.setUpdatedBy(userId);
        record.setSortOrder(0);
        record.setVersion(0);
        recordMapper.insert(record);

        // 为每个可编辑 fieldId 创建 BitableCellValue；只读/计算字段由系统在查询时合成。
        if (dto.getCells() != null && !dto.getCells().isEmpty()) {
            for (Map.Entry<Long, CellValueDTO> entry : dto.getCells().entrySet()) {
                Long fieldId = entry.getKey();
                CellValueDTO cellDTO = entry.getValue();
                BitableField field = requireEditableField(tableId, fieldId);
                if (isLinkFieldType(field.getFieldType())) {
                    linkService.linkRecords(fieldId, record.getId(), extractLongList(cellDTO.getValueJson()), userId);
                    continue;
                }

                BitableCellValue cell = new BitableCellValue();
                cell.setRecordId(record.getId());
                cell.setFieldId(fieldId);
                copyCellValue(cellDTO, cell);
                cellMapper.saveOrUpdateCell(cell);
            }
        }

        // 触发自动化事件
        try {
            automationService.onRecordChanged(tableId, record.getId(), "record_created", null);
        } catch (Exception e) {
            // 自动化事件发布失败不影响主流程
            log.warn("自动化事件发布失败: tableId={}, recordId={}", tableId, record.getId(), e);
        }

        return record.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRecord(Long id, BitableRecordCreateDTO dto, Long userId) {
        BitableRecord existing = recordMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("记录不存在");
        }

        // 乐观锁更新 record
        UpdateWrapper<BitableRecord> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id)
                .eq("version", existing.getVersion())
                .set("updated_by", userId)
                .set("version", existing.getVersion() + 1);

        int updated = recordMapper.update(null, wrapper);
        if (updated <= 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "记录已被他人修改，请刷新后重试");
        }

        // 更新所有可编辑 cells；关联字段通过 LinkService 写入，确保双向关联同步。
        if (dto.getCells() != null && !dto.getCells().isEmpty()) {
            for (Map.Entry<Long, CellValueDTO> entry : dto.getCells().entrySet()) {
                Long fieldId = entry.getKey();
                CellValueDTO cellDTO = entry.getValue();
                BitableField field = requireEditableField(existing.getTableId(), fieldId);
                if (isLinkFieldType(field.getFieldType())) {
                    linkService.linkRecords(fieldId, id, extractLongList(cellDTO.getValueJson()), userId);
                    continue;
                }

                BitableCellValue cell = new BitableCellValue();
                cell.setRecordId(id);
                cell.setFieldId(fieldId);
                copyCellValue(cellDTO, cell);
                cellMapper.saveOrUpdateCell(cell);
            }
        }

        // 触发自动化事件
        try {
            Map<String, Object> changedFields = new HashMap<>();
            if (dto.getCells() != null) {
                for (Map.Entry<Long, CellValueDTO> entry : dto.getCells().entrySet()) {
                    changedFields.put(String.valueOf(entry.getKey()), Map.of(
                            "newValue", entry.getValue().getValueText() != null ? entry.getValue().getValueText() : entry.getValue().getValueJson()
                    ));
                }
            }
            automationService.onRecordChanged(existing.getTableId(), id, "record_updated", changedFields);
        } catch (Exception e) {
            log.warn("自动化事件发布失败: tableId={}, recordId={}", existing.getTableId(), id, e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRecord(Long id) {
        BitableRecord existing = recordMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("记录不存在");
        }

        // 物理删 cell_values
        cellMapper.deleteByRecordId(id);

        // 软删 comments（@TableLogic 自动处理）
        LambdaQueryWrapper<BitableComment> commentWrapper = new LambdaQueryWrapper<>();
        commentWrapper.eq(BitableComment::getRecordId, id);
        commentMapper.delete(commentWrapper);

        // 软删 record（@TableLogic 自动设置 deleted_at=1）
        recordMapper.deleteById(id);

        // 触发自动化事件
        try {
            automationService.onRecordChanged(existing.getTableId(), id, "record_deleted", null);
        } catch (Exception e) {
            log.warn("自动化事件发布失败: tableId={}, recordId={}", existing.getTableId(), id, e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long batchCreateRecords(Long tableId, List<BitableRecordCreateDTO> dtos, Long userId) {
        Long lastId = null;
        for (BitableRecordCreateDTO dto : dtos) {
            lastId = createRecord(tableId, dto, userId);
        }
        return lastId;
    }

    private void copyCellValue(CellValueDTO source, BitableCellValue target) {
        target.setValueText(source.getValueText());
        target.setValueNumber(source.getValueNumber());
        target.setValueDate(parseFlexibleDate(source.getValueDate()));
        target.setValueJson(BitableJsonUtils.toJsonString(source.getValueJson()));
    }

    /**
     * 将前端提交的日期值（字符串/ LocalDate / LocalDateTime）安全解析为 LocalDate。
     * 兼容 "yyyy-MM-dd" 与 "yyyy-MM-dd HH:mm:ss" 两种格式；空值/非法值返回 null，不抛异常。
     * 说明：当前 bitable_cell_values.value_date 为 DATE 类型，仅保留日期部分。
     */
    private static LocalDate parseFlexibleDate(Object date) {
        if (date == null) {
            return null;
        }
        if (date instanceof LocalDate ld) {
            return ld;
        }
        if (date instanceof LocalDateTime ldt) {
            return ldt.toLocalDate();
        }
        if (date instanceof String s) {
            String text = s.trim();
            if (text.isEmpty()) {
                return null;
            }
            // 先尝试纯日期
            try {
                return LocalDate.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            } catch (Exception ignored) {
                // 再尝试带时间的格式，仅保留日期部分
                try {
                    return LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).toLocalDate();
                } catch (Exception e) {
                    log.warn("无法解析的日期值: {}", text);
                    return null;
                }
            }
        }
        return null;
    }

    private BitableCellValueVO toCellValueVO(BitableCellValue cell) {
        BitableCellValueVO vo = new BitableCellValueVO();
        vo.setId(cell.getId());
        vo.setRecordId(cell.getRecordId());
        vo.setFieldId(cell.getFieldId());
        vo.setValueText(cell.getValueText());
        vo.setValueNumber(cell.getValueNumber());
        vo.setValueDate(cell.getValueDate());
        vo.setValueJson(BitableJsonUtils.parseJson(cell.getValueJson()));
        vo.setCreatedAt(cell.getCreatedAt());
        vo.setUpdatedAt(cell.getUpdatedAt());
        return vo;
    }


    private void appendComputedCells(BitableRecordVO record, List<BitableField> fields) {
        if (record.getCells() == null) {
            record.setCells(new LinkedHashMap<>());
        }
        for (BitableField field : fields) {
            String type = field.getFieldType();
            BitableCellValueVO computed = null;
            try {
                if (isSystemFieldType(type)) {
                    computed = buildSystemCell(record, field);
                } else if ("auto_number".equals(type)) {
                    computed = buildAutoNumberCell(record, field);
                } else if ("lookup".equals(type)) {
                    computed = buildLookupCell(record, field);
                } else if ("rollup".equals(type)) {
                    computed = buildRollupCell(record, field);
                } else if ("formula".equals(type)) {
                    computed = buildFormulaCell(record, field, fields);
                }
            } catch (Exception e) {
                computed = buildErrorComputedCell(record, field, e.getMessage());
            }
            if (computed != null) {
                record.getCells().put(field.getId(), computed);
            }
        }
    }

    private boolean isSystemFieldType(String type) {
        return "created_by".equals(type) || "created_user".equals(type)
                || "modified_by".equals(type) || "modified_user".equals(type)
                || "created_time".equals(type) || "last_modified_time".equals(type) || "modified_time".equals(type);
    }

    private boolean isReadonlyComputedFieldType(String type) {
        return isSystemFieldType(type) || "auto_number".equals(type) || "formula".equals(type)
                || "lookup".equals(type) || "rollup".equals(type) || "button".equals(type);
    }

    private boolean isLinkFieldType(String type) {
        return "link".equals(type) || "bidirectional_link".equals(type);
    }

    private BitableField requireEditableField(Long tableId, Long fieldId) {
        BitableField field = fieldMapper.selectById(fieldId);
        if (field == null || !Objects.equals(field.getTableId(), tableId)) {
            throw new BusinessException("字段不存在");
        }
        if (isReadonlyComputedFieldType(field.getFieldType())) {
            throw new BusinessException("该字段为只读/计算字段，不能手动编辑");
        }
        return field;
    }

    private BitableCellValueVO buildBaseComputedCell(BitableRecordVO record, BitableField field) {
        BitableCellValueVO vo = new BitableCellValueVO();
        vo.setRecordId(record.getId());
        vo.setFieldId(field.getId());
        vo.setCreatedAt(record.getCreatedAt());
        vo.setUpdatedAt(record.getUpdatedAt());
        return vo;
    }

    private BitableCellValueVO buildErrorComputedCell(BitableRecordVO record, BitableField field, String message) {
        BitableCellValueVO vo = buildBaseComputedCell(record, field);
        vo.setValueText("#ERROR");
        vo.setValueJson(Map.of("error", message == null ? "计算失败" : message));
        return vo;
    }

    private BitableCellValueVO buildSystemCell(BitableRecordVO record, BitableField field) {
        BitableCellValueVO vo = buildBaseComputedCell(record, field);
        String type = field.getFieldType();
        if ("created_by".equals(type) || "created_user".equals(type)) {
            vo.setValueText(record.getCreatedByName() != null ? record.getCreatedByName() : userNameResolver.resolveUserName(record.getCreatedBy(), "未知用户"));
            vo.setValueJson(Map.of("userId", record.getCreatedBy()));
        } else if ("modified_by".equals(type) || "modified_user".equals(type)) {
            vo.setValueText(record.getUpdatedByName() != null ? record.getUpdatedByName() : userNameResolver.resolveUserName(record.getUpdatedBy(), "未知用户"));
            vo.setValueJson(Map.of("userId", record.getUpdatedBy()));
        } else if ("created_time".equals(type)) {
            vo.setValueText(formatDateTime(record.getCreatedAt(), field));
        } else if ("last_modified_time".equals(type) || "modified_time".equals(type)) {
            vo.setValueText(formatDateTime(record.getUpdatedAt(), field));
        }
        return vo;
    }

    private BitableCellValueVO buildAutoNumberCell(BitableRecordVO record, BitableField field) {
        BitableCellValueVO vo = buildBaseComputedCell(record, field);
        Map<String, Object> config = parseConfig(field);
        String prefix = asString(config.getOrDefault("prefix", ""));
        String suffix = asString(config.getOrDefault("suffix", ""));
        int digits = asInt(config.get("digits"), asInt(config.get("length"), 4));
        String datePart = "";
        String dateFormat = asString(config.get("dateFormat"));
        if (dateFormat != null && !dateFormat.isBlank() && record.getCreatedAt() != null) {
            try {
                datePart = record.getCreatedAt().format(DateTimeFormatter.ofPattern(dateFormat));
            } catch (IllegalArgumentException ignored) {
                datePart = "";
            }
        }
        vo.setValueText(prefix + datePart + String.format("%0" + Math.max(digits, 1) + "d", record.getId()) + suffix);
        return vo;
    }

    private BitableCellValueVO buildLookupCell(BitableRecordVO record, BitableField field) {
        Map<String, Object> config = parseConfig(field);
        Long targetFieldId = asLong(firstNonNull(config.get("targetFieldId"), config.get("lookupFieldId")));
        if (targetFieldId == null) {
            return null;
        }
        List<Object> values = formulaService.calculateLookup(field.getId(), record.getId(), targetFieldId);
        BitableCellValueVO vo = buildBaseComputedCell(record, field);
        vo.setValueJson(values);
        vo.setValueText(values.stream().map(String::valueOf).collect(Collectors.joining(", ")));
        return vo;
    }

    private BitableCellValueVO buildRollupCell(BitableRecordVO record, BitableField field) {
        Map<String, Object> config = parseConfig(field);
        Long targetFieldId = asLong(firstNonNull(config.get("targetFieldId"), config.get("rollupFieldId")));
        String aggregation = asString(firstNonNull(config.get("aggregation"), config.get("function"), config.get("aggregate")));
        if (targetFieldId == null) {
            return null;
        }
        Object result = formulaService.calculateRollup(field.getId(), record.getId(), targetFieldId, aggregation == null ? "count" : aggregation);
        BitableCellValueVO vo = buildBaseComputedCell(record, field);
        if (result instanceof BigDecimal bd) {
            vo.setValueNumber(bd);
        } else if (result instanceof Number num) {
            vo.setValueNumber(new BigDecimal(num.toString()));
        } else if (result != null) {
            vo.setValueText(String.valueOf(result));
        }
        return vo;
    }

    private BitableCellValueVO buildFormulaCell(BitableRecordVO record, BitableField field, List<BitableField> fields) {
        Map<String, Object> config = parseConfig(field);
        String formula = asString(firstNonNull(config.get("formulaExpr"), config.get("formula")));
        if (formula == null || formula.isBlank()) {
            return null;
        }
        Map<String, Object> values = new HashMap<>();
        for (BitableField candidate : fields) {
            BitableCellValueVO cell = record.getCells().get(candidate.getId());
            if (cell == null) continue;
            Object value = extractCellValue(cell);
            values.put("f" + candidate.getId(), value);
            values.put(candidate.getName(), value);
            formula = formula.replace("{" + candidate.getName() + "}", "f" + candidate.getId());
        }
        Object result = formulaService.evaluateFormula(formula, values);
        BitableCellValueVO vo = buildBaseComputedCell(record, field);
        if (result instanceof BigDecimal bd) {
            vo.setValueNumber(bd);
        } else if (result instanceof Number num) {
            vo.setValueNumber(new BigDecimal(num.toString()));
        } else if (result != null) {
            vo.setValueText(String.valueOf(result));
        }
        return vo;
    }

    private Object extractCellValue(BitableCellValueVO cell) {
        if (cell.getValueNumber() != null) return cell.getValueNumber();
        if (cell.getValueDate() != null) return cell.getValueDate();
        if (cell.getValueJson() != null) return cell.getValueJson();
        return cell.getValueText();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(BitableField field) {
        Object parsed = BitableJsonUtils.parseJson(field.getConfig());
        return parsed instanceof Map ? (Map<String, Object>) parsed : Collections.emptyMap();
    }

    private String formatDateTime(LocalDateTime dateTime, BitableField field) {
        if (dateTime == null) return "";
        String pattern = asString(parseConfig(field).get("format"));
        if (pattern == null || pattern.isBlank()) {
            pattern = "yyyy-MM-dd HH:mm:ss";
        }
        try {
            return dateTime.format(DateTimeFormatter.ofPattern(pattern));
        } catch (IllegalArgumentException e) {
            return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) return value;
        }
        return null;
    }

    private List<Long> extractLongListFromUpdateValue(Object value) {
        if (value instanceof CellValueDTO dto) {
            return extractLongList(dto.getValueJson());
        }
        if (value instanceof Map<?, ?> map) {
            if (map.containsKey("valueJson")) {
                return extractLongList(map.get("valueJson"));
            }
            if (map.containsKey("targetRecordIds")) {
                return extractLongList(map.get("targetRecordIds"));
            }
        }
        return extractLongList(value);
    }

    private List<Long> extractLongList(Object rawValue) {
        Object parsed = rawValue instanceof String str ? BitableJsonUtils.parseJson(str) : rawValue;
        if (parsed == null) {
            return Collections.emptyList();
        }
        if (parsed instanceof Number number) {
            return List.of(number.longValue());
        }
        if (parsed instanceof Collection<?> collection) {
            List<Long> ids = new ArrayList<>();
            for (Object item : collection) {
                Long id = asLong(item);
                if (id != null) {
                    ids.add(id);
                }
            }
            return ids;
        }
        Long id = asLong(parsed);
        return id == null ? Collections.emptyList() : List.of(id);
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long asLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private int asInt(Object value, int defaultValue) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer updateCell(Long recordId, Long fieldId, Object value, Integer version, Long userId) {
        // 1. 查询 Record 确认存在且 version 匹配
        BitableRecord existing = recordMapper.selectById(recordId);
        if (existing == null) {
            throw new BusinessException("记录不存在");
        }
        if (!existing.getVersion().equals(version)) {
            throw new BusinessException(ErrorCode.CONFLICT, "记录已被他人修改，请刷新后重试");
        }
        BitableField field = fieldMapper.selectById(fieldId);
        if (field == null || !Objects.equals(field.getTableId(), existing.getTableId())) {
            throw new BusinessException("字段不存在");
        }
        if (isReadonlyComputedFieldType(field.getFieldType())) {
            throw new BusinessException("该字段为只读/计算字段，不能手动编辑");
        }

        // 2. 乐观锁更新 Record 的 updated_by 和 version
        UpdateWrapper<BitableRecord> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", recordId)
                .eq("version", version)
                .set("updated_by", userId)
                .set("version", version + 1);

        int updated = recordMapper.update(null, wrapper);
        if (updated <= 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "记录已被他人修改，请刷新后重试");
        }

        // 3. 插入或更新 CellValue。关联字段走 LinkService，保证双向关联反向同步。
        if (isLinkFieldType(field.getFieldType())) {
            linkService.linkRecords(fieldId, recordId, extractLongListFromUpdateValue(value), userId);
            broadcastCellUpdated(existing.getTableId(), recordId, fieldId, value, version + 1, userId);
            return version + 1;
        }

        BitableCellValue cell = new BitableCellValue();
        cell.setRecordId(recordId);
        cell.setFieldId(fieldId);
        if (value instanceof CellValueDTO) {
            copyCellValue((CellValueDTO) value, cell);
        } else if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            if (map.containsKey("valueText")) {
                cell.setValueText((String) map.get("valueText"));
            }
            if (map.containsKey("valueNumber")) {
                Object num = map.get("valueNumber");
                if (num instanceof Number) {
                    cell.setValueNumber(new java.math.BigDecimal(num.toString()));
                }
            }
            if (map.containsKey("valueDate")) {
                cell.setValueDate(parseFlexibleDate(map.get("valueDate")));
            }
            if (map.containsKey("valueJson")) {
                cell.setValueJson(BitableJsonUtils.toJsonString(map.get("valueJson")));
            }
        } else if (value instanceof String) {
            cell.setValueText((String) value);
        } else if (value instanceof Number) {
            cell.setValueNumber(new java.math.BigDecimal(value.toString()));
        }
        cellMapper.saveOrUpdateCell(cell);

        // 触发自动化事件
        try {
            Map<String, Object> changedFields = new HashMap<>();
            changedFields.put(String.valueOf(fieldId), Map.of("newValue", value));
            automationService.onRecordChanged(existing.getTableId(), recordId, "record_updated", changedFields);
        } catch (Exception e) {
            log.warn("自动化事件发布失败: tableId={}, recordId={}", existing.getTableId(), recordId, e);
        }

        // 4. 返回新版本号
        broadcastCellUpdated(existing.getTableId(), recordId, fieldId, value, version + 1, userId);
        return version + 1;
    }

    /**
     * 写库成功后广播单元格更新（事务提交后执行，确保其他客户端读到的是已提交的新值）。
     * <p>
     * 统一由 REST 写库路径广播，WS 上行不再二次写库，消除单用户编辑的 version 乐观锁竞态。
     */
    private void broadcastCellUpdated(Long tableId, Long recordId, Long fieldId, Object value, Integer newVersion, Long userId) {
        Long baseId;
        BitableTable table = tableMapper.selectById(tableId);
        if (table == null) {
            log.warn("广播单元格更新失败：table 不存在 tableId={}", tableId);
            return;
        }
        baseId = table.getBaseId();

        // 仅在事务中注册 afterCommit；无事务时直接广播。
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    collaborationService.handleCellUpdate(baseId, tableId, recordId, fieldId, value, newVersion, userId);
                }
            });
        } else {
            collaborationService.handleCellUpdate(baseId, tableId, recordId, fieldId, value, newVersion, userId);
        }
    }

    // ==================== 筛选/排序/分组查询 ====================

    @Override
    public PageResult<BitableRecordVO> queryRecords(Long tableId, RecordQueryDTO query) {
        // 1. 如果传了 viewId，从视图配置加载筛选/排序
        resolveViewConfig(query);

        // 2. 加载所有字段定义（用于字段类型判断和计算字段）
        List<BitableField> fields = fieldMapper.selectByTableId(tableId);
        Map<Long, BitableField> fieldMap = fields.stream()
                .collect(Collectors.toMap(BitableField::getId, f -> f));

        // 3. 查询该表所有记录（两阶段查询：先查全部记录，应用层筛选排序后分页）
        int totalRecords = recordMapper.countByTableId(tableId);
        List<BitableRecord> allRecords = totalRecords > 0
                ? recordMapper.selectByTableId(tableId, 0, totalRecords)
                : Collections.emptyList();

        // 4. 批量查单元格值
        Map<Long, Map<Long, BitableCellValueVO>> recordCellsMap = new LinkedHashMap<>();
        if (!allRecords.isEmpty()) {
            List<Long> recordIds = allRecords.stream().map(BitableRecord::getId).collect(Collectors.toList());
            List<BitableCellValue> cells = cellMapper.selectByRecordIds(recordIds);
            List<BitableCellValueVO> cellVOs = cells.stream().map(this::toCellValueVO).toList();
            for (BitableCellValueVO cellVO : cellVOs) {
                recordCellsMap.computeIfAbsent(cellVO.getRecordId(), k -> new LinkedHashMap<>())
                        .put(cellVO.getFieldId(), cellVO);
            }
        }

        // 5. 组装 VO 列表（含计算字段）
        List<BitableRecordVO> voList = converter.toRecordVOList(allRecords);
        for (BitableRecordVO vo : voList) {
            vo.setCells(recordCellsMap.getOrDefault(vo.getId(), new LinkedHashMap<>()));
            vo.setCreatedByName(userNameResolver.resolveUserName(vo.getCreatedBy(), "未知用户"));
            vo.setUpdatedByName(userNameResolver.resolveUserName(vo.getUpdatedBy(), "未知用户"));
            appendComputedCells(vo, fields);
        }

        // 6. 应用层筛选
        List<BitableRecordVO> filtered = applyFilter(voList, query.getFilterConfig(), fieldMap);

        // 7. 应用层排序
        List<BitableRecordVO> sorted = applySort(filtered, query.getSortConfig(), fieldMap);

        // 8. 应用层分页
        int total = sorted.size();
        int pageNum = query.getPageNum() != null ? query.getPageNum() : 1;
        int pageSize = query.getPageSize() != null ? query.getPageSize() : 100;
        int fromIndex = Math.min((pageNum - 1) * pageSize, total);
        int toIndex = Math.min(fromIndex + pageSize, total);
        List<BitableRecordVO> pageData = sorted.subList(fromIndex, toIndex);

        return new PageResult<>(pageData, total, pageNum, pageSize);
    }

    @Override
    public List<RecordGroupVO> queryGroupedRecords(Long tableId, RecordQueryDTO query) {
        // 1. 如果传了 viewId，从视图配置加载筛选/排序/分组
        resolveViewConfig(query);

        // 2. 加载所有字段定义
        List<BitableField> fields = fieldMapper.selectByTableId(tableId);
        Map<Long, BitableField> fieldMap = fields.stream()
                .collect(Collectors.toMap(BitableField::getId, f -> f));

        // 3. 查询所有记录
        int totalRecords = recordMapper.countByTableId(tableId);
        List<BitableRecord> allRecords = totalRecords > 0
                ? recordMapper.selectByTableId(tableId, 0, totalRecords)
                : Collections.emptyList();

        // 4. 批量查单元格值
        Map<Long, Map<Long, BitableCellValueVO>> recordCellsMap = new LinkedHashMap<>();
        if (!allRecords.isEmpty()) {
            List<Long> recordIds = allRecords.stream().map(BitableRecord::getId).collect(Collectors.toList());
            List<BitableCellValue> cells = cellMapper.selectByRecordIds(recordIds);
            List<BitableCellValueVO> cellVOs = cells.stream().map(this::toCellValueVO).toList();
            for (BitableCellValueVO cellVO : cellVOs) {
                recordCellsMap.computeIfAbsent(cellVO.getRecordId(), k -> new LinkedHashMap<>())
                        .put(cellVO.getFieldId(), cellVO);
            }
        }

        // 5. 组装 VO 列表
        List<BitableRecordVO> voList = converter.toRecordVOList(allRecords);
        for (BitableRecordVO vo : voList) {
            vo.setCells(recordCellsMap.getOrDefault(vo.getId(), new LinkedHashMap<>()));
            vo.setCreatedByName(userNameResolver.resolveUserName(vo.getCreatedBy(), "未知用户"));
            vo.setUpdatedByName(userNameResolver.resolveUserName(vo.getUpdatedBy(), "未知用户"));
            appendComputedCells(vo, fields);
        }

        // 6. 应用层筛选
        List<BitableRecordVO> filtered = applyFilter(voList, query.getFilterConfig(), fieldMap);

        // 7. 应用层排序
        List<BitableRecordVO> sorted = applySort(filtered, query.getSortConfig(), fieldMap);

        // 8. 应用层分组
        Long groupByFieldId = query.getGroupByFieldId();
        if (groupByFieldId == null) {
            // 无分组字段，返回单组
            return List.of(new RecordGroupVO("", sorted));
        }

        // 按分组字段值聚合
        Map<String, List<BitableRecordVO>> groupMap = new LinkedHashMap<>();
        for (BitableRecordVO record : sorted) {
            String groupKey = extractGroupKey(record, groupByFieldId, fieldMap);
            groupMap.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(record);
        }

        // 转为 RecordGroupVO 列表
        List<RecordGroupVO> groups = new ArrayList<>();
        for (Map.Entry<String, List<BitableRecordVO>> entry : groupMap.entrySet()) {
            groups.add(new RecordGroupVO(entry.getKey(), entry.getValue()));
        }

        return groups;
    }

    // ==================== 私有辅助方法：视图配置解析 ====================

    /**
     * 如果 query 传了 viewId，从视图配置加载筛选/排序/分组，
     * 与直接传入的参数合并（直接传入优先）
     */
    private void resolveViewConfig(RecordQueryDTO query) {
        if (query.getViewId() == null) {
            return;
        }
        BitableViewVO view = viewService.getViewById(query.getViewId());
        if (view == null) {
            return;
        }
        // 视图配置作为默认值，直接传入的参数优先
        if (query.getFilterConfig() == null && view.getFilterConfig() != null) {
            query.setFilterConfig(view.getFilterConfig());
        }
        if (query.getSortConfig() == null && view.getSortConfig() != null) {
            query.setSortConfig(view.getSortConfig());
        }
        if (query.getGroupByFieldId() == null && view.getGroupConfig() != null) {
            // 从 groupConfig 提取第一个分组字段ID
            Long groupFieldId = extractGroupFieldIdFromConfig(view.getGroupConfig());
            if (groupFieldId != null) {
                query.setGroupByFieldId(groupFieldId);
            }
        }
    }

    /**
     * 从 groupConfig 中提取第一个分组字段ID。
     * groupConfig 格式：[{fieldId: 101, direction: "asc"}, ...]
     */
    @SuppressWarnings("unchecked")
    private Long extractGroupFieldIdFromConfig(Object groupConfig) {
        if (groupConfig instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map<?, ?> map) {
                Object fieldId = map.get("fieldId");
                return asLong(fieldId);
            }
        }
        return null;
    }

    // ==================== 私有辅助方法：筛选 ====================

    /**
     * 应用层筛选。
     * filterConfig 支持两种格式：
     * 1. 简单数组：[{fieldId, operator, value, conjunction}, ...]（默认 AND 逻辑）
     * 2. 嵌套逻辑：{logic: "and"/"or", rules: [...]}
     */
    private List<BitableRecordVO> applyFilter(List<BitableRecordVO> records, Object filterConfig,
                                               Map<Long, BitableField> fieldMap) {
        if (filterConfig == null) {
            return records;
        }

        // 解析为规则列表和逻辑关系
        List<FilterRule> rules = new ArrayList<>();
        String effectiveLogic = "and";

        if (filterConfig instanceof List<?> list) {
            // 简单数组格式
            for (Object item : list) {
                FilterRule rule = parseFilterRule(item);
                if (rule != null) {
                    rules.add(rule);
                }
            }
        } else if (filterConfig instanceof Map<?, ?> map) {
            // 嵌套逻辑格式
            Object logicObj = map.get("logic");
            if (logicObj instanceof String ls) {
                effectiveLogic = ls.toLowerCase();
            }
            Object rulesObj = map.get("rules");
            if (rulesObj instanceof List<?> list) {
                for (Object item : list) {
                    FilterRule rule = parseFilterRule(item);
                    if (rule != null) {
                        rules.add(rule);
                    }
                }
            }
        }

        if (rules.isEmpty()) {
            return records;
        }

        final String finalLogic = effectiveLogic;
        final List<FilterRule> finalRules = new ArrayList<>(rules);

        return records.stream()
                .filter(record -> matchesRecord(record, finalRules, finalLogic, fieldMap))
                .collect(Collectors.toList());
    }

    private boolean matchesRecord(BitableRecordVO record, List<FilterRule> rules, String logic,
                                   Map<Long, BitableField> fieldMap) {
        if ("or".equals(logic)) {
            for (FilterRule rule : rules) {
                if (matchesRule(record, rule, fieldMap)) return true;
            }
            return false;
        } else {
            for (FilterRule rule : rules) {
                if (!matchesRule(record, rule, fieldMap)) return false;
            }
            return true;
        }
    }

    /**
     * 解析单个筛选规则
     */
    @SuppressWarnings("unchecked")
    private FilterRule parseFilterRule(Object item) {
        if (!(item instanceof Map<?, ?> map)) {
            return null;
        }
        Long fieldId = asLong(map.get("fieldId"));
        String operator = asString(map.get("operator"));
        if (fieldId == null || operator == null) {
            return null;
        }
        Object value = map.get("value");
        String conjunction = asString(map.get("conjunction"));
        if (conjunction == null) {
            conjunction = "and";
        }
        return new FilterRule(fieldId, operator, value, conjunction);
    }

    /**
     * 判断单条记录是否满足筛选条件
     */
    private boolean applyFilterVO(BitableRecordVO record, List<FilterRule> rules, String logic,
                                   Map<Long, BitableField> fieldMap) {
        if ("or".equals(logic)) {
            // OR 逻辑：任一条件满足即通过
            for (FilterRule rule : rules) {
                if (matchesRule(record, rule, fieldMap)) {
                    return true;
                }
            }
            return false;
        } else {
            // AND 逻辑（默认）：所有条件都满足才通过
            for (FilterRule rule : rules) {
                if (!matchesRule(record, rule, fieldMap)) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * 判断单条记录是否满足单个筛选规则
     */
    private boolean matchesRule(BitableRecordVO record, FilterRule rule,
                                Map<Long, BitableField> fieldMap) {
        BitableCellValueVO cell = record.getCells() != null
                ? record.getCells().get(rule.fieldId) : null;

        String operator = rule.operator.toLowerCase();
        Object filterValue = rule.value;

        switch (operator) {
            case "is_empty":
                return isCellEmpty(cell);
            case "is_not_empty":
                return !isCellEmpty(cell);
            case "eq":
            case "equals":
                return compareCell(cell, filterValue, fieldMap.get(rule.fieldId)) == 0;
            case "ne":
            case "not_equals":
                return compareCell(cell, filterValue, fieldMap.get(rule.fieldId)) != 0;
            case "contains":
                return containsValue(cell, filterValue);
            case "not_contains":
                return !containsValue(cell, filterValue);
            case "gt":
                return compareCell(cell, filterValue, fieldMap.get(rule.fieldId)) > 0;
            case "lt":
                return compareCell(cell, filterValue, fieldMap.get(rule.fieldId)) < 0;
            case "gte":
                return compareCell(cell, filterValue, fieldMap.get(rule.fieldId)) >= 0;
            case "lte":
                return compareCell(cell, filterValue, fieldMap.get(rule.fieldId)) <= 0;
            default:
                // 未知操作符，不筛选（放行）
                return true;
        }
    }

    /**
     * 判断单元格是否为空
     */
    private boolean isCellEmpty(BitableCellValueVO cell) {
        if (cell == null) return true;
        if (cell.getValueText() != null && !cell.getValueText().isBlank()) return false;
        if (cell.getValueNumber() != null) return false;
        if (cell.getValueDate() != null) return false;
        if (cell.getValueJson() != null) {
            if (cell.getValueJson() instanceof Collection<?> col && !col.isEmpty()) return false;
            if (cell.getValueJson() instanceof Map<?, ?> map && !map.isEmpty()) return false;
            if (cell.getValueJson() instanceof String s && !s.isBlank()) return false;
        }
        return true;
    }

    /**
     * 比较单元格值与筛选值。
     * 返回：负数=cell<filterValue, 0=相等, 正数=cell>filterValue
     */
    private int compareCell(BitableCellValueVO cell, Object filterValue, BitableField field) {
        if (cell == null && filterValue == null) return 0;
        if (cell == null) return -1;
        if (filterValue == null) return 1;

        // 优先按数字比较
        if (cell.getValueNumber() != null) {
            BigDecimal cellNum = cell.getValueNumber();
            BigDecimal filterNum = toBigDecimal(filterValue);
            if (filterNum != null) {
                return cellNum.compareTo(filterNum);
            }
        }

        // 日期比较
        if (cell.getValueDate() != null && filterValue instanceof String dateStr) {
            try {
                java.time.LocalDate filterDate = java.time.LocalDate.parse(dateStr);
                return cell.getValueDate().compareTo(filterDate);
            } catch (Exception ignored) {
                // 日期解析失败，降级为文本比较
            }
        }

        // 文本比较
        String cellText = cell.getValueText();
        if (cellText != null) {
            String filterText = asString(filterValue);
            if (filterText != null) {
                return cellText.compareToIgnoreCase(filterText);
            }
        }

        return 0;
    }

    /**
     * 判断单元格是否包含指定值（文本包含）
     */
    private boolean containsValue(BitableCellValueVO cell, Object filterValue) {
        if (cell == null || filterValue == null) return false;
        String filterText = asString(filterValue);
        if (filterText == null || filterText.isBlank()) return true;

        // 检查文本值
        if (cell.getValueText() != null
                && cell.getValueText().toLowerCase().contains(filterText.toLowerCase())) {
            return true;
        }

        // 检查 valueJson 中的选项（多选场景）
        if (cell.getValueJson() instanceof Collection<?> col) {
            for (Object item : col) {
                String itemStr = asString(item);
                if (itemStr != null && itemStr.toLowerCase().contains(filterText.toLowerCase())) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 将对象转为 BigDecimal
     */
    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return new BigDecimal(n.toString());
        if (value instanceof String s && !s.isBlank()) {
            try {
                return new BigDecimal(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    // ==================== 私有辅助方法：排序 ====================

    /**
     * 应用层排序。
     * sortConfig 格式：[{fieldId: 101, direction: "asc"}, ...]
     * 多个排序项按优先级从高到低排列
     */
    @SuppressWarnings("unchecked")
    private List<BitableRecordVO> applySort(List<BitableRecordVO> records, Object sortConfig,
                                             Map<Long, BitableField> fieldMap) {
        if (sortConfig == null || !(sortConfig instanceof List<?> list) || list.isEmpty()) {
            return records;
        }

        // 解析排序规则
        List<SortRule> sortRules = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            Long fieldId = asLong(map.get("fieldId"));
            String direction = asString(map.get("direction"));
            if (fieldId == null) continue;
            sortRules.add(new SortRule(fieldId, "desc".equalsIgnoreCase(direction) ? -1 : 1));
        }

        if (sortRules.isEmpty()) {
            return records;
        }

        // 多字段排序：使用 Comparator 链
        records.sort((r1, r2) -> {
            for (SortRule rule : sortRules) {
                int cmp = compareRecordByField(r1, r2, rule.fieldId, fieldMap);
                if (cmp != 0) {
                    return cmp * rule.direction;
                }
            }
            // 所有排序字段都相等时，按 ID 升序保持稳定
            return Long.compare(r1.getId(), r2.getId());
        });

        return records;
    }

    /**
     * 按指定字段比较两条记录的值
     */
    private int compareRecordByField(BitableRecordVO r1, BitableRecordVO r2,
                                      Long fieldId, Map<Long, BitableField> fieldMap) {
        BitableCellValueVO c1 = r1.getCells() != null ? r1.getCells().get(fieldId) : null;
        BitableCellValueVO c2 = r2.getCells() != null ? r2.getCells().get(fieldId) : null;

        // 空值排最后
        boolean empty1 = isCellEmpty(c1);
        boolean empty2 = isCellEmpty(c2);
        if (empty1 && empty2) return 0;
        if (empty1) return 1;
        if (empty2) return -1;

        // 数字比较
        if (c1.getValueNumber() != null && c2.getValueNumber() != null) {
            return c1.getValueNumber().compareTo(c2.getValueNumber());
        }

        // 日期比较
        if (c1.getValueDate() != null && c2.getValueDate() != null) {
            return c1.getValueDate().compareTo(c2.getValueDate());
        }

        // 文本比较
        String t1 = c1.getValueText() != null ? c1.getValueText() : "";
        String t2 = c2.getValueText() != null ? c2.getValueText() : "";
        return t1.compareToIgnoreCase(t2);
    }

    // ==================== 私有辅助方法：分组 ====================

    /**
     * 提取记录的分组键值
     */
    private String extractGroupKey(BitableRecordVO record, Long groupByFieldId,
                                    Map<Long, BitableField> fieldMap) {
        BitableCellValueVO cell = record.getCells() != null
                ? record.getCells().get(groupByFieldId) : null;

        if (isCellEmpty(cell)) {
            return "(空)";
        }

        // 优先使用文本值
        if (cell.getValueText() != null && !cell.getValueText().isBlank()) {
            return cell.getValueText();
        }

        // 数字值
        if (cell.getValueNumber() != null) {
            return cell.getValueNumber().toPlainString();
        }

        // 日期值
        if (cell.getValueDate() != null) {
            return cell.getValueDate().toString();
        }

        // JSON值（多选等）
        if (cell.getValueJson() != null) {
            return String.valueOf(cell.getValueJson());
        }

        return "(空)";
    }

    // ==================== 内部数据类 ====================

    /** 筛选规则 */
    private static class FilterRule {
        final Long fieldId;
        final String operator;
        final Object value;
        final String conjunction;

        FilterRule(Long fieldId, String operator, Object value, String conjunction) {
            this.fieldId = fieldId;
            this.operator = operator;
            this.value = value;
            this.conjunction = conjunction;
        }
    }

    /** 排序规则 */
    private static class SortRule {
        final Long fieldId;
        final int direction; // 1=asc, -1=desc

        SortRule(Long fieldId, int direction) {
            this.fieldId = fieldId;
            this.direction = direction;
        }
    }
}