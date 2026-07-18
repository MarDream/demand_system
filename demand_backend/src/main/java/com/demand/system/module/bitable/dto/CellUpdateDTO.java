package com.demand.system.module.bitable.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 单元格更新DTO，用于 updateCell 端点
 */
public class CellUpdateDTO {

    /** 乐观锁版本号（必填） */
    private Integer version;

    private String valueText;

    private BigDecimal valueNumber;

    private LocalDate valueDate;

    private Object valueJson;

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

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
