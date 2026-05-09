package com.demand.system.module.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("workflow_instance_transitions")
public class WorkflowInstanceTransition {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long instanceId;

    private Long requirementId;

    private String fromNodeId;

    private String fromNodeName;

    private String toNodeId;

    private String toNodeName;

    private Long operatorId;

    private String action;

    private String comment;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private Long durationSeconds;

    private LocalDateTime createdAt;
}
