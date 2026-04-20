package com.demand.system.module.relation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("requirement_relations")
public class RequirementRelation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sourceId;

    private Long targetId;

    private String relationType;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
