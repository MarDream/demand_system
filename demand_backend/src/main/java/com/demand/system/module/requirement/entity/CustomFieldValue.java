package com.demand.system.module.requirement.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("requirement_custom_field_values")
public class CustomFieldValue {

    @TableId
    private Long id;

    private Long requirementId;

    private Long fieldId;

    private String valueText;

    private BigDecimal valueNumber;

    private LocalDate valueDate;

    private String valueUserIds;
}
