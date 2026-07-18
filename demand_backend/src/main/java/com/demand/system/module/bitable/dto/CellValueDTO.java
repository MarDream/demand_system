package com.demand.system.module.bitable.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 单元格值DTO，用于记录创建/更新时传递单元格数据
 */
public class CellValueDTO {

    private String valueText;

    private BigDecimal valueNumber;

    private LocalDate valueDate;

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

    public LocalDate getValueDate() {
        return valueDate;
    }

    public void setValueDate(LocalDate valueDate) {
        this.valueDate = valueDate;
    }

    public Object getValueJson() {
        return valueJson;
    }

    public void setValueJson(Object valueJson) {
        this.valueJson = valueJson;
    }
}
