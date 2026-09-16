package com.demand.system.module.bitable.util;

import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.bitable.entity.BitableCellValue;
import com.demand.system.module.bitable.entity.BitableField;
import com.demand.system.module.bitable.mapper.BitableCellMapper;
import com.demand.system.module.bitable.mapper.BitableFieldMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 多维表格「唯一」字段约束校验器。
 * <p>
 * 字段属性面板中的「不允许重复」（{@code config.unique}）由此落地：记录创建 / 更新 / 单元格编辑 /
 * 数据导入等所有写入路径都应先经过本校验，保证同一数据表内该字段值不重复。
 * <p>
 * 语义与飞书对齐：
 * <ul>
 *   <li>空值不参与唯一性判断（允许多行都不填）；</li>
 *   <li>文本忽略首尾空白与大小写；</li>
 *   <li>数字按数值比较（1 与 1.0 视为相同）；</li>
 *   <li>日期未开启「包含时间」时只比日期部分；</li>
 *   <li>多选 / 关联等 JSON 值按规范化后的原文比较。</li>
 * </ul>
 */
@Component
public class BitableUniqueConstraintChecker {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final BitableFieldMapper fieldMapper;
    private final BitableCellMapper cellMapper;

    public BitableUniqueConstraintChecker(BitableFieldMapper fieldMapper, BitableCellMapper cellMapper) {
        this.fieldMapper = fieldMapper;
        this.cellMapper = cellMapper;
    }

    /**
     * 校验待写入单元格是否违反「唯一」约束。
     *
     * @param tableId         数据表 ID
     * @param incoming        本次待写入的单元格（可含非唯一字段，内部会自行过滤）
     * @param excludeRecordId 需要排除的记录 ID（更新场景排除自身），新建传 {@code null}
     * @throws BusinessException 命中冲突时抛出，由外层事务回滚
     */
    public void check(Long tableId, List<BitableCellValue> incoming, Long excludeRecordId) {
        if (incoming == null || incoming.isEmpty()) {
            return;
        }
        Map<Long, BitableField> fieldById = new HashMap<>();
        for (BitableField field : fieldMapper.selectByTableId(tableId)) {
            fieldById.put(field.getId(), field);
        }

        for (BitableCellValue cell : incoming) {
            BitableField field = fieldById.get(cell.getFieldId());
            if (field == null || !isUniqueField(field)) {
                continue;
            }
            String key = cellValueKey(cell);
            if (key == null) {
                // 空值不参与唯一性校验
                continue;
            }
            for (BitableCellValue other : cellMapper.selectByFieldId(field.getId())) {
                if (excludeRecordId != null && excludeRecordId.equals(other.getRecordId())) {
                    continue;
                }
                if (key.equals(cellValueKey(other))) {
                    throw new BusinessException("字段「" + field.getName() + "」的值已存在，该字段要求唯一");
                }
            }
        }
    }

    /**
     * 批量校验（导入场景）：收集全部冲突字段名后一次性报错，便于用户一次改完。
     *
     * @return 冲突字段名列表，无冲突时为空
     */
    public List<String> collectConflicts(Long tableId, List<BitableCellValue> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return List.of();
        }
        Map<Long, BitableField> fieldById = new HashMap<>();
        for (BitableField field : fieldMapper.selectByTableId(tableId)) {
            fieldById.put(field.getId(), field);
        }
        List<String> conflicts = new java.util.ArrayList<>();
        for (BitableCellValue cell : incoming) {
            BitableField field = fieldById.get(cell.getFieldId());
            if (field == null || !isUniqueField(field)) {
                continue;
            }
            String key = cellValueKey(cell);
            if (key == null) {
                continue;
            }
            for (BitableCellValue other : cellMapper.selectByFieldId(field.getId())) {
                if (key.equals(cellValueKey(other))) {
                    if (!conflicts.contains(field.getName())) {
                        conflicts.add(field.getName());
                    }
                    break;
                }
            }
        }
        return conflicts;
    }

    /** 字段是否开启了「唯一」属性（配置存于 {@code config.unique}，兼容 boolean / 0-1 / "true"）。 */
    public boolean isUniqueField(BitableField field) {
        if (field == null || field.getConfig() == null || field.getConfig().isBlank()) {
            return false;
        }
        Object parsed = BitableJsonUtils.parseJson(field.getConfig());
        if (!(parsed instanceof Map<?, ?> map)) {
            return false;
        }
        Object raw = map.get("unique");
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw instanceof Number n) {
            return n.intValue() != 0;
        }
        return raw != null && Boolean.parseBoolean(String.valueOf(raw));
    }

    /**
     * 归一化单元格值为可比较的字符串键；空值返回 {@code null}。
     */
    public static String cellValueKey(BitableCellValue cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getValueNumber() != null) {
            return "N:" + cell.getValueNumber().stripTrailingZeros().toPlainString();
        }
        if (cell.getValueText() != null && !cell.getValueText().isBlank()) {
            return "T:" + cell.getValueText().trim().toLowerCase(Locale.ROOT);
        }
        if (cell.getValueDate() != null) {
            LocalDateTime dateTime = cell.getValueDate();
            return dateTime.toLocalTime().equals(LocalTime.MIDNIGHT)
                    ? "D:" + dateTime.toLocalDate()
                    : "D:" + dateTime.format(DATE_TIME_FORMATTER);
        }
        if (cell.getValueJson() != null && !cell.getValueJson().isBlank()) {
            return "J:" + cell.getValueJson().trim();
        }
        return null;
    }
}
