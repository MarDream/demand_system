package com.demand.system.module.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("workflow_transition_records")
public class WorkflowTransitionRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long requirementId;

    private Long fromStateId;

    private Long toStateId;

    private Long operatorId;

    private String comment;

    private LocalDateTime createdAt;
}
