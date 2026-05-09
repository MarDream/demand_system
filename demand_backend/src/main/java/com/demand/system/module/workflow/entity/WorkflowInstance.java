package com.demand.system.module.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("workflow_instances")
public class WorkflowInstance {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long requirementId;

    private Long workflowVersionId;

    private String currentNodeId;

    private String previousNodeId;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
