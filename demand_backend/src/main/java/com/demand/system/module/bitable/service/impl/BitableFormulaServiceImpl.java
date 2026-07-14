package com.demand.system.module.bitable.service.impl;

import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.bitable.entity.BitableCellValue;
import com.demand.system.module.bitable.entity.BitableField;
import com.demand.system.module.bitable.mapper.BitableCellMapper;
import com.demand.system.module.bitable.mapper.BitableFieldMapper;
import com.demand.system.module.bitable.service.BitableFormulaService;
import com.demand.system.module.bitable.service.BitableLinkService;
import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlExpression;
import org.apache.commons.jexl3.MapContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 多维表格-公式引擎 Service 实现
 * 使用 Apache Commons JEXL3 表达式引擎
 */
@Service
public class BitableFormulaServiceImpl implements BitableFormulaService {

    private final JexlEngine jexl;
    private final BitableFieldMapper fieldMapper;
    private final BitableCellMapper cellMapper;
    private final BitableLinkService linkService;

    public BitableFormulaServiceImpl(BitableFieldMapper fieldMapper,
                                     BitableCellMapper cellMapper,
                                     BitableLinkService linkService) {
        this.fieldMapper = fieldMapper;
        this.cellMapper = cellMapper;
        this.linkService = linkService;
        this.jexl = new JexlBuilder().create();
    }

    @Override
    public Object evaluateFormula(String formula, Map<String, Object> fieldValues) {
        if (formula == null || formula.isBlank()) {
            return null;
        }

        try {
            JexlExpression expr = jexl.createExpression(formula);
            JexlContext ctx = new MapContext();
            for (Map.Entry<String, Object> entry : fieldValues.entrySet()) {
                ctx.set(entry.getKey(), entry.getValue());
            }
            return expr.evaluate(ctx);
        } catch (Exception e) {
            throw new BusinessException("公式计算失败: " + e.getMessage());
        }
    }

    @Override
    public Object calculateRollup(Long fieldId, Long recordId, Long targetFieldId, String aggregation) {
        // 验证字段存在
        BitableField rollupField = fieldMapper.selectById(fieldId);
        if (rollupField == null) {
            throw new BusinessException("rollup 字段不存在");
        }
        if (!"rollup".equals(rollupField.getFieldType())) {
            throw new BusinessException("字段不是 rollup 类型");
        }

        // 获取关联字段信息（从 config 中解析）
        // config 格式示例: {"linkFieldId":123,"targetFieldId":456,"aggregation":"sum"}
        Long linkFieldId = parseLinkFieldId(rollupField);
        if (linkFieldId == null) {
            throw new BusinessException("rollup 字段缺少关联字段配置");
        }

        // 获取关联记录ID列表
        List<Long> linkedRecordIds = linkService.getLinkedRecordIds(linkFieldId, recordId);
        if (linkedRecordIds.isEmpty()) {
            return getDefaultRollupResult(aggregation);
        }

        // 批量查询目标字段值
        List<Object> values = new ArrayList<>();
        for (Long linkedRecordId : linkedRecordIds) {
            BitableCellValue cell = cellMapper.selectByRecordAndField(linkedRecordId, targetFieldId);
            if (cell != null) {
                Object value = extractCellValue(cell);
                if (value != null) {
                    values.add(value);
                }
            }
        }

        // 聚合计算
        return aggregate(values, aggregation);
    }

    @Override
    public List<Object> calculateLookup(Long fieldId, Long recordId, Long targetFieldId) {
        // 验证字段存在
        BitableField lookupField = fieldMapper.selectById(fieldId);
        if (lookupField == null) {
            throw new BusinessException("lookup 字段不存在");
        }
        if (!"lookup".equals(lookupField.getFieldType())) {
            throw new BusinessException("字段不是 lookup 类型");
        }

        // 获取关联字段信息
        Long linkFieldId = parseLinkFieldId(lookupField);
        if (linkFieldId == null) {
            throw new BusinessException("lookup 字段缺少关联字段配置");
        }

        // 获取关联记录ID列表
        List<Long> linkedRecordIds = linkService.getLinkedRecordIds(linkFieldId, recordId);
        if (linkedRecordIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量查询目标字段值
        List<Object> results = new ArrayList<>();
        for (Long linkedRecordId : linkedRecordIds) {
            BitableCellValue cell = cellMapper.selectByRecordAndField(linkedRecordId, targetFieldId);
            if (cell != null) {
                Object value = extractCellValue(cell);
                if (value != null) {
                    results.add(value);
                }
            }
        }

        return results;
    }

    /**
     * 提取单元格值
     */
    private Object extractCellValue(BitableCellValue cell) {
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
            return cell.getValueJson();
        }
        return null;
    }

    /**
     * 聚合计算
     */
    private Object aggregate(List<Object> values, String aggregation) {
        if (values.isEmpty()) {
            return getDefaultRollupResult(aggregation);
        }

        if ("count".equals(aggregation)) {
            return values.size();
        }

        // 对于 sum/average/min/max，需要数值类型
        List<BigDecimal> numbers = new ArrayList<>();
        for (Object v : values) {
            if (v instanceof BigDecimal) {
                numbers.add((BigDecimal) v);
            } else if (v instanceof Number) {
                numbers.add(new BigDecimal(v.toString()));
            } else if (v instanceof String) {
                try {
                    numbers.add(new BigDecimal((String) v));
                } catch (NumberFormatException ignored) {
                    // 非数值跳过
                }
            }
        }

        if (numbers.isEmpty()) {
            return getDefaultRollupResult(aggregation);
        }

        switch (aggregation) {
            case "sum":
                BigDecimal sum = BigDecimal.ZERO;
                for (BigDecimal n : numbers) {
                    sum = sum.add(n);
                }
                return sum;
            case "average":
                BigDecimal total = BigDecimal.ZERO;
                for (BigDecimal n : numbers) {
                    total = total.add(n);
                }
                return total.divide(BigDecimal.valueOf(numbers.size()), 2, RoundingMode.HALF_UP);
            case "min":
                BigDecimal min = numbers.get(0);
                for (BigDecimal n : numbers) {
                    if (n.compareTo(min) < 0) {
                        min = n;
                    }
                }
                return min;
            case "max":
                BigDecimal max = numbers.get(0);
                for (BigDecimal n : numbers) {
                    if (n.compareTo(max) > 0) {
                        max = n;
                    }
                }
                return max;
            default:
                throw new BusinessException("不支持的聚合方式: " + aggregation);
        }
    }

    /**
     * 获取默认聚合结果
     */
    private Object getDefaultRollupResult(String aggregation) {
        if ("count".equals(aggregation)) {
            return 0;
        }
        return null;
    }

    /**
     * 从字段 config 中解析关联字段ID
     */
    private Long parseLinkFieldId(BitableField field) {
        String config = field.getConfig();
        if (config == null || config.isBlank()) {
            return null;
        }
        try {
            // 简单解析 JSON，提取 linkFieldId
            // 实际项目应该用 Jackson 或 Gson
            if (config.contains("\"linkFieldId\"")) {
                int start = config.indexOf("\"linkFieldId\":");
                if (start >= 0) {
                    int colonPos = config.indexOf(':', start);
                    int commaPos = config.indexOf(',', colonPos);
                    int bracePos = config.indexOf('}', colonPos);
                    int endPos = Math.min(commaPos >= 0 ? commaPos : Integer.MAX_VALUE,
                            bracePos >= 0 ? bracePos : Integer.MAX_VALUE);
                    if (colonPos >= 0 && endPos > colonPos) {
                        String numStr = config.substring(colonPos + 1, endPos).trim();
                        return Long.parseLong(numStr);
                    }
                }
            }
        } catch (Exception e) {
            // 解析失败
        }
        return null;
    }
}