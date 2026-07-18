package com.demand.system.module.bitable.service;

import java.util.List;

/**
 * 多维表格-公式依赖关系 Service
 */
public interface BitableFormulaDependencyService {

    /**
     * 解析公式中的字段引用，更新依赖表
     *
     * @param formulaFieldId 公式字段ID
     * @param formulaExpr    公式表达式
     */
    void updateDependencies(Long formulaFieldId, String formulaExpr);

    /**
     * 检测循环引用，返回循环链路（如 A->B->C->A），无循环返回 null
     *
     * @param formulaFieldId 公式字段ID
     * @return 循环链路字段ID列表，无循环返回 null
     */
    List<Long> detectCycle(Long formulaFieldId);

    /**
     * 获取依赖指定字段的所有公式字段（级联）
     *
     * @param fieldId 被依赖的字段ID
     * @return 公式字段ID列表
     */
    List<Long> getDependentFormulaFieldIds(Long fieldId);

    /**
     * 删除指定公式字段的所有依赖
     *
     * @param formulaFieldId 公式字段ID
     */
    void deleteDependencies(Long formulaFieldId);
}
