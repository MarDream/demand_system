package com.demand.system.module.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("workflow_transitions")
public class WorkflowTransition {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private Long fromStateId;

    private Long toStateId;

    private String allowedRoles;

    private String requiredFields;

    private String conditions;
}
