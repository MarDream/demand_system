package com.demand.system.module.bitable.dto;

import java.math.BigDecimal;

/**
 * 单元格更新DTO，用于 updateCell 端点
 *
 * 注意：valueDate 使用 String 类型，前端统一以字符串("yyyy-MM-dd"或"yyyy-MM-dd HH:mm:ss")提交，
 * 由 Service 层统一解析。避免 Jackson 将带时间的字符串强转 LocalDate 失败导致 400/500。
 */
public class CellUpdateDTO {

    /** 乐观锁版本号（必填） */
    private Integer version;

    private String valueText;

    private BigDecimal valueNumber;

    private String valueDate;

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
