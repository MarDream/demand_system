package com.demand.system.module.bitable.service.impl;

import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.PageResult;
import com.demand.system.module.bitable.constant.FieldType;
import com.demand.system.module.bitable.dto.BitableCellValueVO;
import com.demand.system.module.bitable.dto.BitableFieldVO;
import com.demand.system.module.bitable.dto.BitableRecordCreateDTO;
import com.demand.system.module.bitable.dto.BitableRecordVO;
import com.demand.system.module.bitable.dto.CellValueDTO;
import com.demand.system.module.bitable.entity.*;
import com.demand.system.module.bitable.mapper.BitableCellMapper;
import com.demand.system.module.bitable.mapper.BitableFieldMapper;
import com.demand.system.module.bitable.mapper.BitableRecordMapper;
import com.demand.system.module.bitable.mapper.BitableTableMapper;
import com.demand.system.module.bitable.service.BitableImportExportService;
import com.demand.system.module.bitable.service.BitableAutomationService;
import com.demand.system.module.bitable.service.BitableRecordService;
import com.demand.system.module.bitable.util.BitableJsonUtils;
import com.demand.system.module.bitable.util.BitableUniqueConstraintChecker;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 多维表格导入导出 Service 实现
 */
@Service
public class BitableImportExportServiceImpl implements BitableImportExportService {

    private static final Logger log = LoggerFactory.getLogger(BitableImportExportServiceImpl.class);

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 导出行数硬上限，超过则记录告警并截断（防止病态数据量拖垮导出） */
    private static final int EXPORT_MAX_ROWS = 100_000;

    /** 分页加载记录的页大小 */
    private static final int EXPORT_PAGE_SIZE = 1000;

    private final BitableTableMapper tableMapper;
    private final BitableFieldMapper fieldMapper;
    private final BitableRecordMapper recordMapper;
    private final BitableCellMapper cellMapper;
    private final BitableRecordService recordService;
    private final BitableAutomationService automationService;
    private final BitableUniqueConstraintChecker uniqueChecker;

    /** 导入行数上限 */
    private static final int IMPORT_MAX_ROWS = 50_000;

    public BitableImportExportServiceImpl(BitableTableMapper tableMapper,
                                            BitableFieldMapper fieldMapper,
                                            BitableRecordMapper recordMapper,
                                            BitableCellMapper cellMapper,
                                            BitableRecordService recordService,
                                            BitableAutomationService automationService,
                                            BitableUniqueConstraintChecker uniqueChecker) {
        this.tableMapper = tableMapper;
        this.fieldMapper = fieldMapper;
        this.recordMapper = recordMapper;
        this.cellMapper = cellMapper;
        this.recordService = recordService;
        this.automationService = automationService;
        this.uniqueChecker = uniqueChecker;
    }

    /**
     * 只读/计算字段类型：导入时跳过这些列（值由系统合成，写入无意义）
     */
    private boolean isImportSkippableType(String fieldType) {
        return "auto_number".equals(fieldType) || "formula".equals(fieldType)
                || "lookup".equals(fieldType) || "rollup".equals(fieldType)
                || "button".equals(fieldType)
                || "created_by".equals(fieldType) || "created_user".equals(fieldType)
                || "modified_by".equals(fieldType) || "modified_user".equals(fieldType)
                || "created_time".equals(fieldType) || "last_modified_time".equals(fieldType)
                || "modified_time".equals(fieldType);
    }

    @Override
    public byte[] exportTableToExcel(Long tableId) {
        BitableTable table = tableMapper.selectById(tableId);
        if (table == null) {
            throw new BusinessException("数据表不存在");
        }

        List<BitableField> fields = fieldMapper.selectByTableId(tableId);

        SXSSFWorkbook workbook = new SXSSFWorkbook(200);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            try {
                Sheet sheet = workbook.createSheet(safeSheetName(table.getName()));

                // 表头样式
                CellStyle headerStyle = workbook.createCellStyle();
                Font headerFont = workbook.createFont();
                headerFont.setBold(true);
                headerStyle.setFont(headerFont);
                headerStyle.setAlignment(HorizontalAlignment.CENTER);

                // 写表头
                Row headerRow = sheet.createRow(0);
                for (int i = 0; i < fields.size(); i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(fields.get(i).getName());
                    cell.setCellStyle(headerStyle);
                }

                // 分页加载记录（含计算字段/系统字段合成结果），逐批写入
                int exportedRows = 0;
                boolean truncated = false;
                while (exportedRows < EXPORT_MAX_ROWS) {
                    PageResult<BitableRecordVO> page = recordService.listRecords(tableId, exportedRows / EXPORT_PAGE_SIZE + 1, EXPORT_PAGE_SIZE);
                    List<BitableRecordVO> records = page.getList();
                    if (records.isEmpty()) {
                        break;
                    }
                    for (BitableRecordVO record : records) {
                        if (exportedRows >= EXPORT_MAX_ROWS) {
                            truncated = true;
                            break;
                        }
                        Row row = sheet.createRow(exportedRows + 1);
                        writeExcelDataRow(row, record, fields);
                        exportedRows++;
                    }
                    if (truncated || records.size() < EXPORT_PAGE_SIZE || exportedRows >= page.getTotal()) {
                        break;
                    }
                }
                if (truncated) {
                    log.warn("导出 Excel 达到 {} 行硬上限，已截断: tableId={}", EXPORT_MAX_ROWS, tableId);
                }

                // SXSSF 自动列宽需先注册跟踪；大表全量计算代价高，改为固定列宽
                for (int i = 0; i < fields.size(); i++) {
                    if (exportedRows <= 5000 && sheet instanceof SXSSFSheet sxssfSheet) {
                        sxssfSheet.trackColumnForAutoSizing(i);
                        sheet.autoSizeColumn(i);
                    } else {
                        sheet.setColumnWidth(i, 18 * 256);
                    }
                }

                workbook.write(out);
                return out.toByteArray();
            } finally {
                // 清理 SXSSF 写盘产生的临时文件
                workbook.dispose();
            }
        } catch (IOException e) {
            throw new BusinessException("导出 Excel 失败: " + e.getMessage());
        }
    }

    private void writeExcelDataRow(Row row, BitableRecordVO record, List<BitableField> fields) {
        for (int colIdx = 0; colIdx < fields.size(); colIdx++) {
            BitableField field = fields.get(colIdx);
            BitableCellValueVO cell = record.getCells() != null ? record.getCells().get(field.getId()) : null;
            if (cell == null) {
                continue;
            }
            Cell excelCell = row.createCell(colIdx);
            // 数值优先（number/currency/rollup/formula 的数值结果），其次日期，最后文本化
            if (cell.getValueNumber() != null) {
                excelCell.setCellValue(cell.getValueNumber().doubleValue());
            } else if (cell.getValueDate() != null) {
                excelCell.setCellValue(formatCellDate(cell.getValueDate(), field));
            } else {
                String text = cellDisplayText(cell, field);
                if (!text.isEmpty()) {
                    excelCell.setCellValue(text);
                }
            }
        }
    }

    @Override
    public byte[] exportTableToCsv(Long tableId) {
        BitableTable table = tableMapper.selectById(tableId);
        if (table == null) {
            throw new BusinessException("数据表不存在");
        }

        List<BitableField> fields = fieldMapper.selectByTableId(tableId);

        StringBuilder sb = new StringBuilder(64 * 1024);

        // 写 UTF-8 BOM 以便 Excel 正确识别
        sb.append('﻿');

        // 表头
        sb.append(fields.stream()
                .map(f -> escapeCsvField(f.getName(), true))
                .collect(Collectors.joining(",")));
        sb.append("\r\n");

        // 分页加载记录（含计算字段/系统字段合成结果），逐批写出
        int exportedRows = 0;
        boolean truncated = false;
        while (exportedRows < EXPORT_MAX_ROWS) {
            PageResult<BitableRecordVO> page = recordService.listRecords(tableId, exportedRows / EXPORT_PAGE_SIZE + 1, EXPORT_PAGE_SIZE);
            List<BitableRecordVO> records = page.getList();
            if (records.isEmpty()) {
                break;
            }
            for (BitableRecordVO record : records) {
                if (exportedRows >= EXPORT_MAX_ROWS) {
                    truncated = true;
                    break;
                }
                List<String> values = new ArrayList<>(fields.size());
                for (BitableField field : fields) {
                    BitableCellValueVO cell = record.getCells() != null ? record.getCells().get(field.getId()) : null;
                    // 数值/日期原样输出（不可能是 CSV 公式注入载体）；文本走转义+注入中和
                    if (cell != null && cell.getValueNumber() != null) {
                        values.add(cell.getValueNumber().toPlainString());
                    } else if (cell != null && cell.getValueDate() != null) {
                        values.add(formatCellDate(cell.getValueDate(), field));
                    } else {
                        values.add(escapeCsvField(cell != null ? cellDisplayText(cell, field) : "", true));
                    }
                }
                sb.append(String.join(",", values));
                sb.append("\r\n");
                exportedRows++;
            }
            if (truncated || records.size() < EXPORT_PAGE_SIZE || exportedRows >= page.getTotal()) {
                break;
            }
        }
        if (truncated) {
            log.warn("导出 CSV 达到 {} 行硬上限，已截断: tableId={}", EXPORT_MAX_ROWS, tableId);
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * CSV 单元格转义：含分隔符/引号/换行时加引号包裹。
     * {@code guardFormulas} 为 true 时中和以 = + - @ 制表符开头的文本，
     * 防止 Excel/WPS 打开导出文件时将单元格当公式执行（CSV 公式注入）。
     */
    private String escapeCsvField(String value, boolean guardFormulas) {
        if (value == null) {
            return "";
        }
        String safe = value;
        if (guardFormulas && !safe.isEmpty()) {
            char first = safe.charAt(0);
            if (first == '=' || first == '+' || first == '@' || first == '\t' || first == '\r'
                    || (first == '-' && !isNumericCsv(safe))) {
                safe = "'" + safe;
            }
        }
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private boolean isNumericCsv(String value) {
        try {
            new BigDecimal(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 按字段配置格式化日期，导出时与网格展示保持一致。
     * <p>
     * 优先读规范键 {@code dateFormat}（兼容历史键 {@code format}）；
     * 未配置时按「包含时间」属性回落：开启输出到秒，关闭只输出日期。
     */
    private String formatCellDate(LocalDateTime dateTime, BitableField field) {
        if (dateTime == null) {
            return "";
        }
        String pattern = null;
        if (field != null && field.getConfig() != null && !field.getConfig().isBlank()) {
            Object parsed = BitableJsonUtils.parseJson(field.getConfig());
            if (parsed instanceof Map<?, ?> map) {
                Object raw = map.get("dateFormat");
                if (raw == null || String.valueOf(raw).isBlank()) {
                    raw = map.get("format");
                }
                if (raw != null && !String.valueOf(raw).isBlank()) {
                    pattern = String.valueOf(raw);
                } else {
                    Object withTime = map.get("withTime");
                    boolean hasTime = withTime instanceof Boolean b ? b
                            : withTime instanceof Number n ? n.intValue() != 0
                            : withTime != null && Boolean.parseBoolean(String.valueOf(withTime));
                    if (hasTime) {
                        pattern = "yyyy-MM-dd HH:mm:ss";
                    }
                }
            }
        }
        if (pattern == null || pattern.isBlank()) {
            return dateTime.format(DATE_FORMATTER);
        }
        try {
            return dateTime.format(DateTimeFormatter.ofPattern(pattern));
        } catch (IllegalArgumentException e) {
            return dateTime.format(DATE_FORMATTER);
        }
    }

    /**
     * 单元格展示文本：优先 valueText，其次数值/日期，最后 JSON 集合拼接（关联/多选等）
     */
    private String cellDisplayText(BitableCellValueVO cell, BitableField field) {
        if (cell.getValueText() != null && !cell.getValueText().isBlank()) {
            return cell.getValueText();
        }
        if (cell.getValueNumber() != null) {
            return cell.getValueNumber().toPlainString();
        }
        if (cell.getValueDate() != null) {
            return formatCellDate(cell.getValueDate(), field);
        }
        Object json = cell.getValueJson();
        if (json instanceof Collection<?> col) {
            return col.stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "));
        }
        if (json != null) {
            return String.valueOf(json);
        }
        return "";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> importFromExcel(Long tableId, byte[] fileBytes, Long userId) {
        BitableTable table = tableMapper.selectById(tableId);
        if (table == null) {
            throw new BusinessException("数据表不存在");
        }

        List<BitableField> fields = fieldMapper.selectByTableId(tableId);
        // 按名称建立查找映射
        Map<String, BitableField> fieldNameMap = new LinkedHashMap<>();
        for (BitableField f : fields) {
            fieldNameMap.put(f.getName().trim(), f);
        }

        List<Long> createdIds = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(fileBytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new BusinessException("Excel 文件中没有工作表");
            }

            // 第一行表头
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BusinessException("Excel 文件第一行必须为表头");
            }

            // 建立列号 -> 字段映射
            Map<Integer, BitableField> colToField = new LinkedHashMap<>();
            for (int col = 0; col < headerRow.getLastCellNum(); col++) {
                Cell headerCell = headerRow.getCell(col);
                if (headerCell == null) {
                    continue;
                }
                String headerName = getCellAsString(headerCell);
                if (headerName == null || headerName.trim().isEmpty()) {
                    continue;
                }
                String trimmedName = headerName.trim();
                // 优先查找已有字段，不匹配则自动创建 text 类型字段
                BitableField field = fieldNameMap.get(trimmedName);
                if (field == null) {
                    field = new BitableField();
                    field.setTableId(tableId);
                    field.setName(trimmedName);
                    field.setFieldType(FieldType.TEXT.getCode());
                    field.setSortOrder(fields.size() + col);
                    fieldMapper.insert(field);
                    fieldNameMap.put(trimmedName, field);
                    fields.add(field);
                }
                // 只读/计算字段由系统合成，导入写入无意义，直接跳过该列
                if (isImportSkippableType(field.getFieldType())) {
                    continue;
                }
                colToField.put(col, field);
            }

            // 遍历数据行（从第二行开始）
            for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                if (rowIdx > IMPORT_MAX_ROWS) {
                    throw new BusinessException("导入数据超过 " + IMPORT_MAX_ROWS + " 行上限，请拆分后分批导入");
                }
                Row row = sheet.getRow(rowIdx);
                if (row == null) {
                    continue;
                }

                // 跳过空行
                if (isRowEmpty(row, colToField.keySet())) {
                    continue;
                }

                // 创建记录
                BitableRecord record = new BitableRecord();
                record.setTableId(tableId);
                record.setCreatedBy(userId);
                record.setUpdatedBy(userId);
                record.setSortOrder(0);
                record.setVersion(0);
                recordMapper.insert(record);

                // 填充单元格
                List<BitableCellValue> rowCells = new ArrayList<>();
                for (Map.Entry<Integer, BitableField> entry : colToField.entrySet()) {
                    Cell cell = row.getCell(entry.getKey());
                    if (cell == null) {
                        continue;
                    }
                    BitableField field = entry.getValue();
                    BitableCellValue cellValue = excelCellToCellValue(record.getId(), field.getId(), cell, field);
                    if (cellValue != null) {
                        rowCells.add(cellValue);
                    }
                }

                // 「唯一」字段校验：冲突时定位到具体行，便于用户回表格修改后重试
                try {
                    uniqueChecker.check(tableId, rowCells, null);
                } catch (BusinessException e) {
                    throw new BusinessException("第 " + (rowIdx + 1) + " 行导入失败：" + e.getMessage());
                }
                for (BitableCellValue cellValue : rowCells) {
                    cellMapper.saveOrUpdateCell(cellValue);
                }

                // 与单条创建保持一致：触发 record_created 自动化事件（失败不影响导入主流程）
                try {
                    automationService.onRecordChanged(tableId, record.getId(), "record_created", null);
                } catch (Exception e) {
                    log.warn("导入触发自动化事件失败: tableId={}, recordId={}", tableId, record.getId(), e);
                }

                createdIds.add(record.getId());
            }

        } catch (IOException e) {
            throw new BusinessException("解析 Excel 文件失败: " + e.getMessage());
        }

        return createdIds;
    }

    private BitableCellValue excelCellToCellValue(Long recordId, Long fieldId, Cell cell, BitableField field) {
        FieldType fieldType = FieldType.fromCode(field.getFieldType());
        if (fieldType == null) {
            String text = getCellAsString(cell);
            if (text == null || text.isEmpty()) {
                return null;
            }
            BitableCellValue value = new BitableCellValue();
            value.setRecordId(recordId);
            value.setFieldId(fieldId);
            value.setValueText(text);
            return value;
        }

        BitableCellValue value = new BitableCellValue();
        value.setRecordId(recordId);
        value.setFieldId(fieldId);

        switch (fieldType) {
            case NUMBER, PROGRESS, RATING -> {
                BigDecimal num = getCellAsNumber(cell);
                if (num != null) {
                    value.setValueNumber(num);
                } else {
                    // 尝试以文本解析
                    String text = getCellAsString(cell);
                    if (text != null && !text.isEmpty()) {
                        try {
                            value.setValueNumber(new BigDecimal(text.trim()));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
            case DATE -> {
                LocalDateTime date = getCellAsDate(cell);
                if (date != null) {
                    value.setValueDate(date);
                } else {
                    String text = getCellAsString(cell);
                    if (text != null && !text.isEmpty()) {
                        value.setValueDate(parseImportDate(text));
                    }
                }
            }
            default -> {
                String text = getCellAsString(cell);
                if (text != null && !text.isEmpty()) {
                    value.setValueText(text);
                }
            }
        }

        // 如果全部字段都为空，则返回 null
        if (value.getValueText() == null && value.getValueNumber() == null && value.getValueDate() == null) {
            return null;
        }
        return value;
    }

    private String getCellAsString(Cell cell) {
        if (cell == null) {
            return null;
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue() != null
                            ? cell.getDateCellValue().toInstant().toString()
                            : null;
                }
                double num = cell.getNumericCellValue();
                if (num == Math.floor(num)) {
                    yield String.valueOf((long) num);
                }
                yield String.valueOf(num);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    try {
                        yield String.valueOf(cell.getNumericCellValue());
                    } catch (Exception ex) {
                        yield null;
                    }
                }
            }
            default -> null;
        };
    }

    private BigDecimal getCellAsNumber(Cell cell) {
        if (cell == null) {
            return null;
        }
        return switch (cell.getCellType()) {
            case NUMERIC -> {
                if (!DateUtil.isCellDateFormatted(cell)) {
                    yield BigDecimal.valueOf(cell.getNumericCellValue());
                }
                yield null;
            }
            case FORMULA -> {
                try {
                    yield BigDecimal.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    yield null;
                }
            }
            default -> null;
        };
    }

    private LocalDateTime getCellAsDate(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            java.util.Date date = cell.getDateCellValue();
            if (date != null) {
                return date.toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime();
            }
        }
        if (cell.getCellType() == CellType.STRING) {
            String text = cell.getStringCellValue().trim();
            if (text.isEmpty()) {
                return null;
            }
            // 先试纯日期，再试带时间的写法
            try {
                return LocalDate.parse(text, DATE_FORMATTER).atStartOfDay();
            } catch (Exception ignored) {
                // 继续尝试带时间
            }
            try {
                return LocalDateTime.parse(text, DATETIME_FORMATTER);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 解析导入文本中的日期，兼容 {@code yyyy-MM-dd} 与 {@code yyyy-MM-dd HH:mm:ss}；
     * 无法解析返回 null（由调用方决定是否降级为文本）。
     */
    private static LocalDateTime parseImportDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        try {
            return LocalDate.parse(trimmed, DATE_FORMATTER).atStartOfDay();
        } catch (Exception ignored) {
            // 继续尝试带时间
        }
        try {
            return LocalDateTime.parse(trimmed, DATETIME_FORMATTER);
        } catch (Exception ignored) {
            // 再试 Excel 常见的斜杠写法
        }
        try {
            return LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("yyyy/M/d")).atStartOfDay();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isRowEmpty(Row row, Set<Integer> cols) {
        if (row == null) {
            return true;
        }
        for (int col : cols) {
            Cell cell = row.getCell(col);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String text = getCellAsString(cell);
                if (text != null && !text.isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private String safeSheetName(String name) {
        if (name == null || name.isEmpty()) {
            return "Sheet1";
        }
        // Excel sheet 名称限制 31 字符，且不能包含 : \ / ? * [ ]
        String safe = name.replaceAll("[\\\\/?*\\[\\]:]", "_");
        return safe.length() > 31 ? safe.substring(0, 31) : safe;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> importFromCsv(Long tableId, String csvContent, Long userId) {
        BitableTable table = tableMapper.selectById(tableId);
        if (table == null) {
            throw new BusinessException("数据表不存在");
        }

        List<BitableField> fields = fieldMapper.selectByTableId(tableId);
        Map<String, BitableField> fieldNameMap = new LinkedHashMap<>();
        for (BitableField f : fields) {
            fieldNameMap.put(f.getName().trim(), f);
        }

        List<Long> createdIds = new ArrayList<>();
        if (csvContent == null || csvContent.isEmpty()) {
            return createdIds;
        }

        List<List<String>> rows = parseCsv(csvContent);
        if (rows.isEmpty()) {
            return createdIds;
        }

        // 第一行为表头
        List<String> header = rows.get(0);
        Map<Integer, BitableField> colToField = new LinkedHashMap<>();
        for (int col = 0; col < header.size(); col++) {
            String headerName = header.get(col);
            if (headerName == null || headerName.trim().isEmpty()) {
                continue;
            }
            String trimmedName = headerName.trim();
            // 优先查找已有字段，不匹配则自动创建 text 类型字段
            BitableField field = fieldNameMap.get(trimmedName);
            if (field == null) {
                field = new BitableField();
                field.setTableId(tableId);
                field.setName(trimmedName);
                field.setFieldType(FieldType.TEXT.getCode());
                field.setSortOrder(fields.size() + col);
                fieldMapper.insert(field);
                fieldNameMap.put(trimmedName, field);
                fields.add(field);
            }
            // 只读/计算字段由系统合成，导入写入无意义，直接跳过该列
            if (isImportSkippableType(field.getFieldType())) {
                continue;
            }
            colToField.put(col, field);
        }

        // 遍历数据行
        for (int rowIdx = 1; rowIdx < rows.size(); rowIdx++) {
            if (rowIdx > IMPORT_MAX_ROWS) {
                throw new BusinessException("导入数据超过 " + IMPORT_MAX_ROWS + " 行上限，请拆分后分批导入");
            }
            List<String> row = rows.get(rowIdx);
            if (row.isEmpty() || row.stream().allMatch(v -> v == null || v.isEmpty())) {
                continue;
            }

            BitableRecord record = new BitableRecord();
            record.setTableId(tableId);
            record.setCreatedBy(userId);
            record.setUpdatedBy(userId);
            record.setSortOrder(0);
            record.setVersion(0);
            recordMapper.insert(record);

            List<BitableCellValue> rowCells = new ArrayList<>();
            for (Map.Entry<Integer, BitableField> entry : colToField.entrySet()) {
                int col = entry.getKey();
                if (col >= row.size()) {
                    continue;
                }
                String cellValue = row.get(col);
                if (cellValue == null || cellValue.isEmpty()) {
                    continue;
                }
                BitableField field = entry.getValue();
                BitableCellValue cell = csvCellToCellValue(record.getId(), field.getId(), cellValue, field);
                if (cell != null) {
                    rowCells.add(cell);
                }
            }

            // 「唯一」字段校验：冲突时定位到具体行，便于用户回文件修改后重试
            try {
                uniqueChecker.check(tableId, rowCells, null);
            } catch (BusinessException e) {
                throw new BusinessException("第 " + (rowIdx + 1) + " 行导入失败：" + e.getMessage());
            }
            for (BitableCellValue cell : rowCells) {
                cellMapper.saveOrUpdateCell(cell);
            }

            // 与单条创建保持一致：触发 record_created 自动化事件（失败不影响导入主流程）
            try {
                automationService.onRecordChanged(tableId, record.getId(), "record_created", null);
            } catch (Exception e) {
                log.warn("导入触发自动化事件失败: tableId={}, recordId={}", tableId, record.getId(), e);
            }

            createdIds.add(record.getId());
        }

        return createdIds;
    }

    private BitableCellValue csvCellToCellValue(Long recordId, Long fieldId, String rawValue, BitableField field) {
        FieldType fieldType = FieldType.fromCode(field.getFieldType());
        BitableCellValue value = new BitableCellValue();
        value.setRecordId(recordId);
        value.setFieldId(fieldId);

        if (fieldType == null) {
            value.setValueText(rawValue);
            return value;
        }

        switch (fieldType) {
            case NUMBER, PROGRESS, RATING -> {
                try {
                    value.setValueNumber(new BigDecimal(rawValue.trim()));
                } catch (NumberFormatException e) {
                    value.setValueText(rawValue);
                }
            }
            case DATE -> {
                LocalDateTime parsed = parseImportDate(rawValue);
                if (parsed != null) {
                    value.setValueDate(parsed);
                } else {
                    value.setValueText(rawValue);
                }
            }
            default -> value.setValueText(rawValue);
        }

        if (value.getValueText() == null && value.getValueNumber() == null && value.getValueDate() == null) {
            return null;
        }
        return value;
    }

    /**
     * 简易 CSV 解析器，支持引号包裹的逗号和双引号转义。
     */
    private List<List<String>> parseCsv(String csv) {
        List<List<String>> rows = new ArrayList<>();
        List<String> currentRow = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;
        boolean fieldStarted = false;

        // 去除 BOM
        String content = csv;
        if (content.startsWith("﻿")) {
            content = content.substring(1);
        }

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);

            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
                        currentField.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    currentField.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                    fieldStarted = true;
                } else if (c == ',') {
                    currentRow.add(currentField.toString());
                    currentField.setLength(0);
                    fieldStarted = false;
                } else if (c == '\r') {
                    // 跳过，等 \n 处理
                } else if (c == '\n') {
                    currentRow.add(currentField.toString());
                    rows.add(new ArrayList<>(currentRow));
                    currentRow.clear();
                    currentField.setLength(0);
                    fieldStarted = false;
                } else {
                    currentField.append(c);
                    fieldStarted = true;
                }
            }
        }

        // 处理最后一行（没有换行符结尾）
        if (fieldStarted || currentField.length() > 0 || !currentRow.isEmpty()) {
            currentRow.add(currentField.toString());
            rows.add(new ArrayList<>(currentRow));
        }

        return rows;
    }
}