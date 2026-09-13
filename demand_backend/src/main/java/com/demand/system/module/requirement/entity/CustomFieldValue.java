package com.demand.system.module.requirement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@TableName("requirement_custom_field_values")
public class CustomFieldValue {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long requirementId;

    private Long fieldId;

    private String valueText;

    private BigDecimal valueNumber;

    private LocalDate valueDate;

    private String valueUserIds;

    private Integer valueBoolean;

    private Long valueUserId;

    private String fieldCodeSnapshot;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRequirementId() {
        return requirementId;
    }

    public void setRequirementId(Long requirementId) {
        this.requirementId = requirementId;
    }

    public Long getFieldId() {
        return fieldId;
    }

    public void setFieldId(Long fieldId) {
        this.fieldId = fieldId;
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

    public String getValueUserIds() {
        return valueUserIds;
    }

    public void setValueUserIds(String valueUserIds) {
        this.valueUserIds = valueUserIds;
    }

    public Integer getValueBoolean() {
        return valueBoolean;
    }

    public void setValueBoolean(Integer valueBoolean) {
        this.valueBoolean = valueBoolean;
    }

    public Long getValueUserId() {
        return valueUserId;
    }

    public void setValueUserId(Long valueUserId) {
        this.valueUserId = valueUserId;
    }

    public String getFieldCodeSnapshot() {
        return fieldCodeSnapshot;
    }

    public void setFieldCodeSnapshot(String fieldCodeSnapshot) {
        this.fieldCodeSnapshot = fieldCodeSnapshot;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomFieldValue that = (CustomFieldValue) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
