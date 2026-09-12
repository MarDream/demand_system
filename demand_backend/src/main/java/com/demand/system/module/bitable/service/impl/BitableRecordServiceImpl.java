package com.demand.system.module.bitable.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.common.result.PageResult;
import com.demand.system.common.util.UserNameResolver;
import com.demand.system.module.bitable.constant.OperationType;
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
import com.demand.system.module.bitable.query.FilterGroupNode;
import com.demand.system.module.bitable.query.FilterNode;
import com.demand.system.module.bitable.query.FilterPredicateNode;
import com.demand.system.module.bitable.query.FilterTreeParser;
import com.demand.system.module.bitable.service.BitableAutomationService;
import com.demand.system.module.bitable.service.BitableCollaborationService;
import com.demand.system.module.bitable.service.BitableFormulaService;
import com.demand.system.module.bitable.service.BitableLinkService;
import com.demand.system.module.bitable.service.BitableRecordService;
import com.demand.system.module.bitable.service.BitableViewService;
import com.demand.system.module.bitable.util.BitableAuditHelper;
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
    private final BitableAuditHelper auditHelper;

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
                                    BitableCollaborationService collaborationService,
                                    BitableAuditHelper auditHelper) {
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
        this.auditHelper = auditHelper;
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
            }
            appendComputedCellsBatch(voList, fields);
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

        // 广播记录创建，让协作端实时新增行
        broadcastRecordChanged(tableId, record.getId(), "record_created", userId);

        // 审计
        auditHelper.recordByTable(tableId, userId, OperationType.INSERT_RECORD, "{\"recordId\":" + record.getId() + "}");

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
                    Object newValue = entry.getValue().getValueText() != null
                            ? entry.getValue().getValueText()
                            : entry.getValue().getValueJson();
                    // Map.of 不允许 null 值（清空单元格是合法操作），先归一
                    Map<String, Object> change = new HashMap<>();
                    change.put("newValue", newValue);
                    changedFields.put(String.valueOf(entry.getKey()), change);
                }
            }
            automationService.onRecordChanged(existing.getTableId(), id, "record_updated", changedFields);
        } catch (Exception e) {
            log.warn("自动化事件发布失败: tableId={}, recordId={}", existing.getTableId(), id, e);
        }

        // 审计
        auditHelper.recordByTable(existing.getTableId(), userId, OperationType.UPDATE_RECORD,
                "{\"recordId\":" + id + ",\"changedFields\":" + (dto.getCells() != null ? dto.getCells().size() : 0) + "}");
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

        // 广播记录删除，让协作端实时移除行
        broadcastRecordChanged(existing.getTableId(), id, "record_deleted", null);

        // 审计
        auditHelper.recordByTable(existing.getTableId(), null, OperationType.DELETE_RECORD, "{\"recordId\":" + id + "}");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long batchCreateRecords(Long tableId, List<BitableRecordCreateDTO> dtos, Long userId) {
        // 单次批量上限，防止超大批量请求拖垮数据库
        final int maxBatchSize = 500;
        if (dtos.size() > maxBatchSize) {
            throw new BusinessException("单次批量创建不能超过 " + maxBatchSize + " 条记录");
        }
        long created = 0;
        for (BitableRecordCreateDTO dto : dtos) {
            createRecord(tableId, dto, userId);
            created++;
        }
        return created;
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
        appendComputedCellsBatch(List.of(record), fields);
    }

    /**
     * 批量计算字段的批量版：系统字段/自动编号/公式逐条内存计算，
     * Lookup/Rollup 通过批量预取关联目标值，把原来"每记录×每字段 2 次 SQL"的 N×M 查询
     * 压缩为"每字段 1 次批量查询"。
     */
    private void appendComputedCellsBatch(List<BitableRecordVO> records, List<BitableField> fields) {
        List<BitableField> lookupFields = new ArrayList<>();
        List<BitableField> rollupFields = new ArrayList<>();
        List<BitableField> formulaFields = new ArrayList<>();
        for (BitableField field : fields) {
            switch (field.getFieldType()) {
                case "lookup" -> lookupFields.add(field);
                case "rollup" -> rollupFields.add(field);
                case "formula" -> formulaFields.add(field);
                default -> { }
            }
        }

        // Pass 1：系统字段 + 自动编号（纯内存）
        for (BitableRecordVO record : records) {
            if (record.getCells() == null) {
                record.setCells(new LinkedHashMap<>());
            }
            for (BitableField field : fields) {
                String type = field.getFieldType();
                try {
                    if (isSystemFieldType(type)) {
                        putComputedCell(record, buildSystemCell(record, field));
                    } else if ("auto_number".equals(type)) {
                        putComputedCell(record, buildAutoNumberCell(record, field));
                    }
                } catch (Exception e) {
                    putComputedCell(record, buildErrorComputedCell(record, field, e.getMessage()));
                }
            }
        }

        // Pass 2：Lookup / Rollup 批量预取
        appendBatchLookupCells(records, lookupFields);
        appendBatchRollupCells(records, rollupFields);

        // Pass 3：公式（最后计算，可引用前面已合成的 lookup/rollup/系统字段值）
        for (BitableRecordVO record : records) {
            for (BitableField field : formulaFields) {
                try {
                    putComputedCell(record, buildFormulaCell(record, field, fields));
                } catch (Exception e) {
                    putComputedCell(record, buildErrorComputedCell(record, field, e.getMessage()));
                }
            }
        }
    }

    private void putComputedCell(BitableRecordVO record, BitableCellValueVO computed) {
        if (computed != null) {
            record.getCells().put(computed.getFieldId(), computed);
        }
    }

    /**
     * 批量计算 Lookup 字段：按字段收集所有关联目标记录 ID，一次 IN 查询取回目标单元格值
     */
    private void appendBatchLookupCells(List<BitableRecordVO> records, List<BitableField> lookupFields) {
        if (lookupFields.isEmpty() || records.isEmpty()) {
            return;
        }

        // 预解析每个 lookup 字段的配置
        for (BitableField field : lookupFields) {
            Map<String, Object> config = parseConfig(field);
            Long targetFieldId = asLong(firstNonNull(config.get("targetFieldId"), config.get("lookupFieldId")));
            Long linkFieldId = asLong(firstNonNull(config.get("linkFieldId"), config.get("linkField")));
            if (targetFieldId == null || linkFieldId == null) {
                // 与原单条路径一致：配置缺失按计算错误处理
                for (BitableRecordVO record : records) {
                    putComputedCell(record, buildErrorComputedCell(record, field, "lookup 字段缺少关联字段配置"));
                }
                continue;
            }

            // 收集各记录的关联记录 ID（来自 link 字段已加载的 valueJson）
            Map<Long, List<Long>> linksByRecord = new LinkedHashMap<>();
            Set<Long> unionTargetIds = new LinkedHashSet<>();
            for (BitableRecordVO record : records) {
                BitableCellValueVO linkCell = record.getCells().get(linkFieldId);
                List<Long> linkedIds = linkCell == null ? Collections.emptyList() : extractLongList(linkCell.getValueJson());
                linksByRecord.put(record.getId(), linkedIds);
                unionTargetIds.addAll(linkedIds);
            }

            // 一次查询取回所有目标单元格值
            Map<Long, BitableCellValue> targetCellMap = loadTargetCells(unionTargetIds, targetFieldId);

            for (BitableRecordVO record : records) {
                List<Object> values = new ArrayList<>();
                for (Long targetId : linksByRecord.getOrDefault(record.getId(), Collections.emptyList())) {
                    BitableCellValue targetCell = targetCellMap.get(targetId);
                    if (targetCell != null) {
                        Object value = extractEntityCellValue(targetCell);
                        if (value != null) {
                            values.add(value);
                        }
                    }
                }
                BitableCellValueVO vo = buildBaseComputedCell(record, field);
                vo.setValueJson(values);
                vo.setValueText(values.stream().map(String::valueOf).collect(Collectors.joining(", ")));
                putComputedCell(record, vo);
            }
        }
    }

    /**
     * 批量计算 Rollup 字段：同样批量预取目标值后内存聚合
     */
    private void appendBatchRollupCells(List<BitableRecordVO> records, List<BitableField> rollupFields) {
        if (rollupFields.isEmpty() || records.isEmpty()) {
            return;
        }

        for (BitableField field : rollupFields) {
            Map<String, Object> config = parseConfig(field);
            Long targetFieldId = asLong(firstNonNull(config.get("targetFieldId"), config.get("rollupFieldId")));
            Long linkFieldId = asLong(firstNonNull(config.get("linkFieldId"), config.get("linkField")));
            String aggregation = asString(firstNonNull(config.get("aggregation"), config.get("function"), config.get("aggregate")));
            if (targetFieldId == null || linkFieldId == null) {
                for (BitableRecordVO record : records) {
                    putComputedCell(record, buildErrorComputedCell(record, field, "rollup 字段缺少关联字段配置"));
                }
                continue;
            }

            Map<Long, List<Long>> linksByRecord = new LinkedHashMap<>();
            Set<Long> unionTargetIds = new LinkedHashSet<>();
            for (BitableRecordVO record : records) {
                BitableCellValueVO linkCell = record.getCells().get(linkFieldId);
                List<Long> linkedIds = linkCell == null ? Collections.emptyList() : extractLongList(linkCell.getValueJson());
                linksByRecord.put(record.getId(), linkedIds);
                unionTargetIds.addAll(linkedIds);
            }

            Map<Long, BitableCellValue> targetCellMap = loadTargetCells(unionTargetIds, targetFieldId);

            for (BitableRecordVO record : records) {
                List<Object> values = new ArrayList<>();
                for (Long targetId : linksByRecord.getOrDefault(record.getId(), Collections.emptyList())) {
                    BitableCellValue targetCell = targetCellMap.get(targetId);
                    if (targetCell != null) {
                        Object value = extractEntityCellValue(targetCell);
                        if (value != null) {
                            values.add(value);
                        }
                    }
                }
                Object result = formulaService.aggregateValues(values, aggregation == null ? "count" : aggregation);
                BitableCellValueVO vo = buildBaseComputedCell(record, field);
                if (result instanceof BigDecimal bd) {
                    vo.setValueNumber(bd);
                } else if (result instanceof Number num) {
                    vo.setValueNumber(new BigDecimal(num.toString()));
                } else if (result != null) {
                    vo.setValueText(String.valueOf(result));
                }
                putComputedCell(record, vo);
            }
        }
    }

    /**
     * 一次 IN 查询取回目标记录集中 targetFieldId 的单元格值，Map<recordId, cell>（重复取首条）
     */
    private Map<Long, BitableCellValue> loadTargetCells(Set<Long> targetRecordIds, Long targetFieldId) {
        if (targetRecordIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<BitableCellValue> cells = cellMapper.selectByRecordIds(new ArrayList<>(targetRecordIds));
        Map<Long, BitableCellValue> map = new HashMap<>();
        for (BitableCellValue cell : cells) {
            if (targetFieldId.equals(cell.getFieldId())) {
                map.putIfAbsent(cell.getRecordId(), cell);
            }
        }
        return map;
    }

    /**
     * 与公式引擎 extractCellValue 同序的实体值提取：number → text → date → json
     */
    private Object extractEntityCellValue(BitableCellValue cell) {
        if (cell.getValueNumber() != null) {
            return cell.getValueNumber();
        }
        if (cell.getValueText() != null && !cell.getValueText().isBlank()) {
            return cell.getValueText();
        }
        if (cell.getValueDate() != null) {
            return cell.getValueDate();
        }
        if (cell.getValueJson() != null && !cell.getValueJson().isBlank()) {
            return BitableJsonUtils.parseJson(cell.getValueJson());
        }
        return null;
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

    /**
     * 写库成功后广播记录创建/删除事件（事务提交后执行）。
     * 让其他在线协作客户端实时感知行的增删。
     */
    private void broadcastRecordChanged(Long tableId, Long recordId, String changeType, Long userId) {
        BitableTable table = tableMapper.selectById(tableId);
        if (table == null) {
            log.warn("广播记录变更失败：table 不存在 tableId={}", tableId);
            return;
        }
        Long baseId = table.getBaseId();

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    collaborationService.broadcastRecordChanged(baseId, tableId, recordId, changeType, userId);
                }
            });
        } else {
            collaborationService.broadcastRecordChanged(baseId, tableId, recordId, changeType, userId);
        }
    }

    // ==================== 筛选/排序/分组查询 ====================

    @Override
    public PageResult<BitableRecordVO> queryRecords(Long tableId, RecordQueryDTO query) {
        // 1. 如果传了 viewId，从视图配置加载筛选/排序
        resolveViewConfig(tableId, query);

        // 2. 加载所有字段定义（用于字段类型判断和计算字段）
        List<BitableField> fields = fieldMapper.selectByTableId(tableId);
        Map<Long, BitableField> fieldMap = fields.stream()
                .collect(Collectors.toMap(BitableField::getId, f -> f));

        // 3. 查询该表所有记录（两阶段查询：先查全部记录，应用层筛选排序后分页）
        // 全量内存查询保护上限：超过 5 万行拒绝执行，提示缩小范围或使用索引字段筛选
        final int MAX_INMEMORY_QUERY_ROWS = 50_000;
        int totalRecords = recordMapper.countByTableId(tableId);
        if (totalRecords > MAX_INMEMORY_QUERY_ROWS) {
            throw new BusinessException("该表数据量过大（" + totalRecords + " 行），无法执行全量筛选查询，请缩小筛选范围");
        }
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

        // 5. 组装 VO 列表（含计算字段，批量预取 Lookup/Rollup）
        List<BitableRecordVO> voList = converter.toRecordVOList(allRecords);
        for (BitableRecordVO vo : voList) {
            vo.setCells(recordCellsMap.getOrDefault(vo.getId(), new LinkedHashMap<>()));
            vo.setCreatedByName(userNameResolver.resolveUserName(vo.getCreatedBy(), "未知用户"));
            vo.setUpdatedByName(userNameResolver.resolveUserName(vo.getUpdatedBy(), "未知用户"));
        }
        appendComputedCellsBatch(voList, fields);

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
        resolveViewConfig(tableId, query);

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
        }
        appendComputedCellsBatch(voList, fields);

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
    private void resolveViewConfig(Long tableId, RecordQueryDTO query) {
        if (query.getViewId() == null) {
            return;
        }
        BitableViewVO view = viewService.getViewById(query.getViewId());
        if (view == null) {
            return;
        }
        // 视图必须属于当前查询的表，防止套用其他表的视图配置导致字段错位
        if (view.getTableId() == null || !view.getTableId().equals(tableId)) {
            log.warn("查询引用了不属于当前表的视图: tableId={}, viewId={}, viewTableId={}",
                    tableId, query.getViewId(), view.getTableId());
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
     * 应用层筛选。筛选配置先解析为统一递归规则树，再执行短路匹配。
     */
    private List<BitableRecordVO> applyFilter(List<BitableRecordVO> records, Object filterConfig,
                                               Map<Long, BitableField> fieldMap) {
        FilterNode filterTree = FilterTreeParser.parse(filterConfig);
        if (filterTree == null) {
            return records;
        }

        return records.stream()
                .filter(record -> matchesNode(record, filterTree, fieldMap))
                .collect(Collectors.toList());
    }

    /** 递归执行逻辑组或字段谓词。 */
    private boolean matchesNode(BitableRecordVO record, FilterNode node,
                                Map<Long, BitableField> fieldMap) {
        if (node instanceof FilterPredicateNode predicate) {
            return matchesPredicate(record, predicate, fieldMap);
        }

        FilterGroupNode group = (FilterGroupNode) node;
        if (group.isOr()) {
            for (FilterNode child : group.children()) {
                if (matchesNode(record, child, fieldMap)) {
                    return true;
                }
            }
            return false;
        }

        for (FilterNode child : group.children()) {
            if (!matchesNode(record, child, fieldMap)) {
                return false;
            }
        }
        return true;
    }

    /** 判断单条字段谓词。 */
    private boolean matchesPredicate(BitableRecordVO record, FilterPredicateNode predicate,
                                     Map<Long, BitableField> fieldMap) {
        BitableCellValueVO cell = record.getCells() != null
                ? record.getCells().get(predicate.fieldId()) : null;
        Object filterValue = predicate.value();
        BitableField field = fieldMap.get(predicate.fieldId());

        return switch (predicate.operator()) {
            case "is_empty" -> isCellEmpty(cell);
            case "is_not_empty" -> !isCellEmpty(cell);
            case "eq", "equals" -> compareCell(cell, filterValue, field) == 0;
            case "ne", "not_equals" -> compareCell(cell, filterValue, field) != 0;
            case "contains" -> containsValue(cell, filterValue);
            case "not_contains" -> !containsValue(cell, filterValue);
            case "gt" -> compareCell(cell, filterValue, field) > 0;
            case "lt" -> compareCell(cell, filterValue, field) < 0;
            case "gte" -> compareCell(cell, filterValue, field) >= 0;
            case "lte" -> compareCell(cell, filterValue, field) <= 0;
            case "between" -> matchesBetween(cell, filterValue, field);
            default -> false;
        };
    }

    /** between 使用闭区间 [min, max]，支持数组和对象两种配置。 */
    private boolean matchesBetween(BitableCellValueVO cell, Object value, BitableField field) {
        if (cell == null || value == null) {
            return false;
        }

        Object min = null;
        Object max = null;
        if (value instanceof List<?> list && list.size() == 2) {
            min = list.get(0);
            max = list.get(1);
        } else if (value instanceof Map<?, ?> map
                && map.containsKey("min") && map.containsKey("max")) {
            min = map.get("min");
            max = map.get("max");
        }
        if (min == null || max == null) {
            return false;
        }

        return compareCell(cell, min, field) >= 0
                && compareCell(cell, max, field) <= 0;
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

        // JSON 值（关联/多选等仅存 valueJson 的字段）：文本化后比较，避免"恒等/恒不等"
        if (cell.getValueJson() != null) {
            String jsonText = asString(cell.getValueJson());
            String filterText = asString(filterValue);
            if (jsonText != null && filterText != null) {
                return jsonText.compareToIgnoreCase(filterText);
            }
        }

        // 双方类型不可比：视为不相等（返回非 0），确保 eq/ne 语义正确
        return -1;
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
        if (!t1.isEmpty() || !t2.isEmpty()) {
            return t1.compareToIgnoreCase(t2);
        }

        // JSON 值（关联/多选等）文本化后比较
        String j1 = c1.getValueJson() != null ? asString(c1.getValueJson()) : "";
        String j2 = c2.getValueJson() != null ? asString(c2.getValueJson()) : "";
        if (j1 == null) j1 = "";
        if (j2 == null) j2 = "";
        return j1.compareToIgnoreCase(j2);
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

        // JSON值（多选等）：集合元素用逗号拼接展示，避免分组键显示原始 JSON
        if (cell.getValueJson() instanceof Collection<?> col) {
            return col.stream().map(item -> item != null ? asString(item) : null)
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.joining(", "));
        }
        if (cell.getValueJson() != null) {
            return asString(cell.getValueJson());
        }

        return "(空)";
    }

    // ==================== 内部数据类 ====================

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