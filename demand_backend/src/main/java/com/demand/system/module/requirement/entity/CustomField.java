package com.demand.system.module.requirement.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("custom_fields")
public class CustomField {

    @TableId
    private Long id;

    private Long projectId;

    private String name;

    private String fieldType;

    private String options;

    private Integer required;

    private String visibleStatuses;

    private String defaultValue;

    private Integer sortOrder;
}
