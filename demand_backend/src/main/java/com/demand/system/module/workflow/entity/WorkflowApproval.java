package com.demand.system.module.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("workflow_approvals")
public class WorkflowApproval {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workflowVersionId;

    private Long submitterId;

    private Long approverId;

    private String status;

    private String comment;

    private LocalDateTime submittedAt;

    private LocalDateTime approvedAt;
}
