package com.demand.system.module.bitable.service;

import java.util.List;
import java.util.Map;

/**
 * 多维表格-公式引擎 Service
 */
public interface BitableFormulaService {

    /**
     * 评估公式表达式
     *
     * @param formula     公式字符串，JEXL 格式
     * @param fieldValues 字段名 -> 值的映射
     * @return 计算结果
     */
    Object evaluateFormula(String formula, Map<String, Object> fieldValues);

    /**
     * 计算 rollup 聚合值
     *
     * @param fieldId       rollup 字段ID
     * @param recordId      当前记录ID
     * @param targetFieldId 目标字段ID
     * @param aggregation   聚合方式: sum/average/count/min/max
     * @return 聚合结果
     */
    Object calculateRollup(Long fieldId, Long recordId, Long targetFieldId, String aggregation);

    /**
     * 计算 lookup 目标字段值列表
     *
     * @param fieldId       lookup 字段ID
     * @param recordId      当前记录ID
     * @param targetFieldId 目标字段ID
     * @return 目标字段值列表
     */
    List<Object> calculateLookup(Long fieldId, Long recordId, Long targetFieldId);
}
