package com.demand.system.module.requirement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 需求动态字段值提交结构。
 * <p>通过不可变的 fieldCode 引用字段，不接受客户端 fieldId，杜绝任意 SQL/跨类型写入。
 */
public class CustomFieldValueDTO {

    /** 稳定字段编码，唯一身份标识。 */
    private String fieldCode;

    /** 文本/单选/URL 单值。 */
    private String value;

    /** 数值单值。 */
    private BigDecimal valueNumber;

    /** 日期单值。 */
    private LocalDate valueDate;

    /** 布尔单值。 */
    private Boolean valueBoolean;

    /** 单人员值。 */
    private Long valueUserId;

    /** 多选选项 / 多人员 / 附件 的多值列表（统一走多值明细表）。 */
    private List<Object> values;

    public String getFieldCode() {
        return fieldCode;
    }

    public void setFieldCode(String fieldCode) {
        this.fieldCode = fieldCode;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
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

    public Boolean getValueBoolean() {
        return valueBoolean;
    }

    public void setValueBoolean(Boolean valueBoolean) {
        this.valueBoolean = valueBoolean;
    }

    public Long getValueUserId() {
        return valueUserId;
    }

    public void setValueUserId(Long valueUserId) {
        this.valueUserId = valueUserId;
    }

    public List<Object> getValues() {
        return values;
    }

    public void setValues(List<Object> values) {
        this.values = values;
    }
}