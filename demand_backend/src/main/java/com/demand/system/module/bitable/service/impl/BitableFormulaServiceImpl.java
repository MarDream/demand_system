package com.demand.system.module.bitable.service.impl;

import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.bitable.constant.FormulaErrorType;
import com.demand.system.module.bitable.entity.BitableCellValue;
import com.demand.system.module.bitable.entity.BitableField;
import com.demand.system.module.bitable.formula.BitableFunctions;
import com.demand.system.module.bitable.mapper.BitableCellMapper;
import com.demand.system.module.bitable.mapper.BitableFieldMapper;
import com.demand.system.module.bitable.service.BitableFormulaDependencyService;
import com.demand.system.module.bitable.service.BitableFormulaService;
import com.demand.system.module.bitable.service.BitableLinkService;
import com.demand.system.module.bitable.util.BitableJsonUtils;
import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlExpression;
import org.apache.commons.jexl3.MapContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 多维表格-公式引擎 Service 实现
 * 使用 Apache Commons JEXL3 表达式引擎
 */
@Service
public class BitableFormulaServiceImpl implements BitableFormulaService {

    private static final Logger log = LoggerFactory.getLogger(BitableFormulaServiceImpl.class);

    /**
     * 匹配 {字段名} 模式的正则
     */
    private static final Pattern FIELD_NAME_PATTERN = Pattern.compile("\\{([^}]+)}");

    /**
     * 匹配 f+fieldId 模式的正则（如 fld123 或 f123）
     */
    private static final Pattern FIELD_ID_PATTERN = Pattern.compile("f(?:ld)?(\\d+)");

    private final JexlEngine jexl;
    private final BitableFieldMapper fieldMapper;
    private final BitableCellMapper cellMapper;
    private final BitableLinkService linkService;
    private final BitableFormulaDependencyService dependencyService;

    public BitableFormulaServiceImpl(BitableFieldMapper fieldMapper,
                                     BitableCellMapper cellMapper,
                                     BitableLinkService linkService,
                                     BitableFormulaDependencyService dependencyService) {
        this.fieldMapper = fieldMapper;
        this.cellMapper = cellMapper;
        this.linkService = linkService;
        this.dependencyService = dependencyService;
        this.jexl = new JexlBuilder()
                .namespaces(Map.of("bf", BitableFunctions.class))
                .strict(false)
                .silent(false)
                .create();
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
        } catch (ArithmeticException e) {
            if (e.getMessage() != null && e.getMessage().contains("/ by zero")) {
                return FormulaErrorType.DIV_ZERO.getDisplay();
            }
            return FormulaErrorType.ERROR.getDisplay();
        } catch (org.apache.commons.jexl3.JexlException.Method e) {
            log.warn("公式方法调用异常: {}", e.getMessage());
            return FormulaErrorType.NAME_ERROR.getDisplay();
        } catch (org.apache.commons.jexl3.JexlException.Property e) {
            log.warn("公式属性引用异常: {}", e.getMessage());
            return FormulaErrorType.REF_ERROR.getDisplay();
        } catch (org.apache.commons.jexl3.JexlException.Operator e) {
            log.warn("公式运算符异常: {}", e.getMessage());
            return FormulaErrorType.TYPE_ERROR.getDisplay();
        } catch (org.apache.commons.jexl3.JexlException.Variable e) {
            log.warn("公式变量引用异常: {}", e.getMessage());
            return FormulaErrorType.REF_ERROR.getDisplay();
        } catch (org.apache.commons.jexl3.JexlException.Parsing e) {
            log.warn("公式解析异常: {}", e.getMessage());
            return FormulaErrorType.ERROR.getDisplay();
        } catch (Exception e) {
            log.warn("公式计算失败: {}", e.getMessage());
            return FormulaErrorType.ERROR.getDisplay();
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
        Long linkFieldId = parseLinkFieldId(rollupField);
        if (linkFieldId == null) {
            throw new BusinessException("rollup 字段缺少关联字段配置");
        }

        // 获取关联记录ID列表
        List<Long> linkedRecordIds = linkService.getLinkedRecordIds(linkFieldId, recordId);
        if (linkedRecordIds.isEmpty()) {
            return getDefaultRollupResult(aggregation);
        }

        // 批量查询目标字段值（优化：一次 IN 查询替代 N+1）
        List<Object> values = batchExtractCellValues(linkedRecordIds, targetFieldId);

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

        // 批量查询目标字段值（优化：一次 IN 查询替代 N+1）
        return batchExtractCellValues(linkedRecordIds, targetFieldId);
    }

    @Override
    public Map<String, Object> validateFormula(String formula, Long tableId) {
        Map<String, Object> result = new HashMap<>();
        result.put("valid", false);

        if (formula == null || formula.isBlank()) {
            result.put("errorType", FormulaErrorType.ERROR.getDisplay());
            result.put("errorMessage", "公式不能为空");
            return result;
        }

        // 1. 解析字段引用
        Set<Long> referencedFieldIds = new HashSet<>();
        Set<String> referencedFieldNames = new HashSet<>();

        // 解析 {字段名} 模式
        Matcher nameMatcher = FIELD_NAME_PATTERN.matcher(formula);
        while (nameMatcher.find()) {
            referencedFieldNames.add(nameMatcher.group(1).trim());
        }

        // 解析 f+fieldId 模式
        Matcher idMatcher = FIELD_ID_PATTERN.matcher(formula);
        while (idMatcher.find()) {
            try {
                referencedFieldIds.add(Long.parseLong(idMatcher.group(1)));
            } catch (NumberFormatException ignored) {
            }
        }

        // 将字段名转为字段ID
        if (!referencedFieldNames.isEmpty() && tableId != null) {
            List<BitableField> fields = fieldMapper.selectByTableId(tableId);
            Map<String, Long> nameToId = fields.stream()
                    .collect(Collectors.toMap(
                            f -> f.getName().trim(),
                            BitableField::getId,
                            (a, b) -> a
                    ));
            for (String name : referencedFieldNames) {
                Long id = nameToId.get(name);
                if (id != null) {
                    referencedFieldIds.add(id);
                } else {
                    result.put("errorType", FormulaErrorType.NAME_ERROR.getDisplay());
                    result.put("errorMessage", "未找到字段: " + name);
                    result.put("referencedFieldIds", new ArrayList<>(referencedFieldIds));
                    return result;
                }
            }
        }

        result.put("referencedFieldIds", new ArrayList<>(referencedFieldIds));

        // 2. 尝试编译公式（语法检查）
        try {
            jexl.createExpression(formula);
        } catch (Exception e) {
            result.put("errorType", FormulaErrorType.ERROR.getDisplay());
            result.put("errorMessage", "公式语法错误: " + e.getMessage());
            return result;
        }

        // 3. 推断结果类型
        String resultType = inferResultType(formula);
        result.put("resultType", resultType);

        result.put("valid", true);
        return result;
    }

    /**
     * 批量提取指定记录列表中某字段的值
     * 优化：使用 selectByRecordIds 一次查询替代 N+1
     *
     * @param recordIds     记录ID列表
     * @param targetFieldId 目标字段ID
     * @return 值列表
     */
    private List<Object> batchExtractCellValues(List<Long> recordIds, Long targetFieldId) {
        if (recordIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 一次 IN 查询获取所有关联记录的单元格值
        List<BitableCellValue> cells = cellMapper.selectByRecordIds(recordIds);

        // 过滤出目标字段的值
        List<Object> values = new ArrayList<>();
        for (BitableCellValue cell : cells) {
            if (targetFieldId.equals(cell.getFieldId())) {
                Object value = extractCellValue(cell);
                if (value != null) {
                    values.add(value);
                }
            }
        }
        return values;
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
    @SuppressWarnings("unchecked")
    private Long parseLinkFieldId(BitableField field) {
        Object parsed = BitableJsonUtils.parseJson(field.getConfig());
        if (!(parsed instanceof Map)) {
            return null;
        }
        Map<String, Object> config = (Map<String, Object>) parsed;
        Object raw = config.get("linkFieldId");
        if (raw == null) {
            raw = config.get("linkField");
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
     * 推断公式结果类型
     * 基于公式中使用的函数和运算符进行简单推断
     */
    private String inferResultType(String formula) {
        if (formula.contains("bf:CONCATENATE") || formula.contains("bf:LEFT")
                || formula.contains("bf:RIGHT") || formula.contains("bf:MID")
                || formula.contains("bf:REPLACE") || formula.contains("bf:ARRAYJOIN")) {
            return "text";
        }
        if (formula.contains("bf:TODAY") || formula.contains("bf:NOW")) {
            return "date";
        }
        if (formula.contains("bf:IF") || formula.contains("bf:IFS")) {
            return "text"; // IF 结果类型不确定，默认 text
        }
        if (formula.contains("bf:SUM") || formula.contains("bf:AVERAGE")
                || formula.contains("bf:MAX") || formula.contains("bf:MIN")
                || formula.contains("bf:ROUND") || formula.contains("bf:CEILING")
                || formula.contains("bf:FLOOR") || formula.contains("bf:MOD")) {
            return "number";
        }
        if (formula.contains("+") || formula.contains("-")
                || formula.contains("*") || formula.contains("/")) {
            return "number";
        }
        return "text";
    }
}
