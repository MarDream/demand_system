package com.demand.system.module.bitable.service.impl;

import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.bitable.constant.FieldType;
import com.demand.system.module.bitable.dto.CellValueDTO;
import com.demand.system.module.bitable.dto.BitableRecordCreateDTO;
import com.demand.system.module.bitable.entity.*;
import com.demand.system.module.bitable.mapper.BitableCellMapper;
import com.demand.system.module.bitable.mapper.BitableFieldMapper;
import com.demand.system.module.bitable.mapper.BitableRecordMapper;
import com.demand.system.module.bitable.mapper.BitableTableMapper;
import com.demand.system.module.bitable.service.BitableImportExportService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 多维表格导入导出 Service 实现
 */
@Service
public class BitableImportExportServiceImpl implements BitableImportExportService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final BitableTableMapper tableMapper;
    private final BitableFieldMapper fieldMapper;
    private final BitableRecordMapper recordMapper;
    private final BitableCellMapper cellMapper;

    public BitableImportExportServiceImpl(BitableTableMapper tableMapper,
                                            BitableFieldMapper fieldMapper,
                                            BitableRecordMapper recordMapper,
                                            BitableCellMapper cellMapper) {
        this.tableMapper = tableMapper;
        this.fieldMapper = fieldMapper;
        this.recordMapper = recordMapper;
        this.cellMapper = cellMapper;
    }

    @Override
    public byte[] exportTableToExcel(Long tableId) {
        BitableTable table = tableMapper.selectById(tableId);
        if (table == null) {
            throw new BusinessException("数据表不存在");
        }

        List<BitableField> fields = fieldMapper.selectByTableId(tableId);
        List<BitableRecord> records = recordMapper.selectByTableId(tableId, 0, 10000);

        List<Long> recordIds = records.stream().map(BitableRecord::getId).collect(Collectors.toList());
        List<BitableCellValue> cells = recordIds.isEmpty()
                ? List.of()
                : cellMapper.selectByRecordIds(recordIds);

        // Map<recordId, Map<fieldId, cell>>
        Map<Long, Map<Long, BitableCellValue>> cellMap = new HashMap<>();
        for (BitableCellValue cell : cells) {
            cellMap.computeIfAbsent(cell.getRecordId(), k -> new HashMap<>())
                    .put(cell.getFieldId(), cell);
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

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

            // 写数据行
            for (int rowIdx = 0; rowIdx < records.size(); rowIdx++) {
                BitableRecord record = records.get(rowIdx);
                Row row = sheet.createRow(rowIdx + 1);
                Map<Long, BitableCellValue> recordCells = cellMap.getOrDefault(record.getId(), Collections.emptyMap());

                for (int colIdx = 0; colIdx < fields.size(); colIdx++) {
                    BitableField field = fields.get(colIdx);
                    BitableCellValue cell = recordCells.get(field.getId());
                    if (cell == null) {
                        continue;
                    }
                    Cell excelCell = row.createCell(colIdx);
                    populateExcelCell(excelCell, cell, field);
                }
            }

            // 自动列宽
            for (int i = 0; i < fields.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new BusinessException("导出 Excel 失败: " + e.getMessage());
        }
    }

    private void populateExcelCell(Cell excelCell, BitableCellValue cell, BitableField field) {
        FieldType fieldType = FieldType.fromCode(field.getFieldType());
        if (fieldType == null) {
            if (cell.getValueText() != null) {
                excelCell.setCellValue(cell.getValueText());
            }
            return;
        }

        switch (fieldType) {
            case NUMBER, PROGRESS, RATING -> {
                if (cell.getValueNumber() != null) {
                    excelCell.setCellValue(cell.getValueNumber().doubleValue());
                }
            }
            case DATE -> {
                if (cell.getValueDate() != null) {
                    excelCell.setCellValue(cell.getValueDate().format(DATE_FORMATTER));
                }
            }
            default -> {
                if (cell.getValueText() != null) {
                    excelCell.setCellValue(cell.getValueText());
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
        List<BitableRecord> records = recordMapper.selectByTableId(tableId, 0, 10000);

        List<Long> recordIds = records.stream().map(BitableRecord::getId).collect(Collectors.toList());
        List<BitableCellValue> cells = recordIds.isEmpty()
                ? List.of()
                : cellMapper.selectByRecordIds(recordIds);

        Map<Long, Map<Long, BitableCellValue>> cellMap = new HashMap<>();
        for (BitableCellValue cell : cells) {
            cellMap.computeIfAbsent(cell.getRecordId(), k -> new HashMap<>())
                    .put(cell.getFieldId(), cell);
        }

        StringBuilder sb = new StringBuilder();

        // 写 UTF-8 BOM 以便 Excel 正确识别
        sb.append('﻿');

        // 表头
        sb.append(fields.stream()
                .map(f -> escapeCsvField(f.getName()))
                .collect(Collectors.joining(",")));
        sb.append("\r\n");

        // 数据行
        for (BitableRecord record : records) {
            Map<Long, BitableCellValue> recordCells = cellMap.getOrDefault(record.getId(), Collections.emptyMap());
            List<String> values = new ArrayList<>();
            for (BitableField field : fields) {
                BitableCellValue cell = recordCells.get(field.getId());
                values.add(cellToCsvString(cell, field));
            }
            sb.append(String.join(",", values));
            sb.append("\r\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String escapeCsvField(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String cellToCsvString(BitableCellValue cell, BitableField field) {
        if (cell == null) {
            return "";
        }
        FieldType fieldType = FieldType.fromCode(field.getFieldType());
        if (fieldType == null) {
            return escapeCsvField(cell.getValueText());
        }

        return switch (fieldType) {
            case NUMBER, PROGRESS, RATING -> cell.getValueNumber() != null
                    ? cell.getValueNumber().toPlainString()
                    : "";
            case DATE -> cell.getValueDate() != null
                    ? cell.getValueDate().format(DATE_FORMATTER)
                    : "";
            default -> escapeCsvField(cell.getValueText());
        };
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
                if (headerName != null) {
                    BitableField field = fieldNameMap.get(headerName.trim());
                    if (field != null) {
                        colToField.put(col, field);
                    }
                }
            }

            // 遍历数据行（从第二行开始）
            for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
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
                for (Map.Entry<Integer, BitableField> entry : colToField.entrySet()) {
                    Cell cell = row.getCell(entry.getKey());
                    if (cell == null) {
                        continue;
                    }
                    BitableField field = entry.getValue();
                    BitableCellValue cellValue = excelCellToCellValue(record.getId(), field.getId(), cell, field);
                    if (cellValue != null) {
                        cellMapper.saveOrUpdateCell(cellValue);
                    }
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
                LocalDate date = getCellAsDate(cell);
                if (date != null) {
                    value.setValueDate(date);
                } else {
                    String text = getCellAsString(cell);
                    if (text != null && !text.isEmpty()) {
                        try {
                            value.setValueDate(LocalDate.parse(text.trim(), DATE_FORMATTER));
                        } catch (Exception ignored) {
                        }
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

    private LocalDate getCellAsDate(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            java.util.Date date = cell.getDateCellValue();
            if (date != null) {
                return date.toInstant()
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate();
            }
        }
        if (cell.getCellType() == CellType.STRING) {
            try {
                return LocalDate.parse(cell.getStringCellValue().trim(), DATE_FORMATTER);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
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
            if (headerName != null) {
                BitableField field = fieldNameMap.get(headerName.trim());
                if (field != null) {
                    colToField.put(col, field);
                }
            }
        }

        // 遍历数据行
        for (int rowIdx = 1; rowIdx < rows.size(); rowIdx++) {
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
                    cellMapper.saveOrUpdateCell(cell);
                }
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
                try {
                    value.setValueDate(LocalDate.parse(rawValue.trim(), DATE_FORMATTER));
                } catch (Exception e) {
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