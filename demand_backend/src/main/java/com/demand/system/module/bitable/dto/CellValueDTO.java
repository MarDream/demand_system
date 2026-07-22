package com.demand.system.module.bitable.dto;

import java.math.BigDecimal;

/**
 * 单元格值DTO，用于记录创建/更新时传递单元格数据
 *
 * 注意：valueDate 使用 String 类型，前端统一以字符串提交，由 Service 层统一解析。
 */
public class CellValueDTO {

    private String valueText;

    private BigDecimal valueNumber;

    private String valueDate;

    private Object valueJson;

    public String getValueText() {
        return valueText;
    }

    public void setValueText(String valueText) {
        this.valueText = valueText;
    }

    public BigDecimal getValueNumber() {
        return valueNumber;
    }

    public void setValueNumber(BigDecimal valueNumber) {
        this.valueNumber = valueNumber;
    }

    public String getValueDate() {
        return valueDate;
    }

    public void setValueDate(String valueDate) {
        this.valueDate = valueDate;
    }

    public Object getValueJson() {
        return valueJson;
    }

    public void setValueJson(Object valueJson) {
        this.valueJson = valueJson;
    }
}
