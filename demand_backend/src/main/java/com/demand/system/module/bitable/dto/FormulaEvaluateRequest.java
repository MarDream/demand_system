package com.demand.system.module.bitable.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * 公式计算请求DTO（测试用）
 */
public class FormulaEvaluateRequest {

    @NotBlank(message = "公式不能为空")
    private String formula;

    private Map<String, Object> fieldValues;

    public String getFormula() {
        return formula;
    }

    public void setFormula(String formula) {
        this.formula = formula;
    }

    public Map<String, Object> getFieldValues() {
        return fieldValues;
    }

    public void setFieldValues(Map<String, Object> fieldValues) {
        this.fieldValues = fieldValues;
    }
}