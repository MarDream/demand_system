package com.demand.system.module.requirement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("requirement_history")
public class RequirementHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long requirementId;

    private Long operatorId;

    private String fieldName;

    private String oldValue;

    private String newValue;

    private LocalDateTime createdAt;
}
