package com.demand.system.module.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("workflow_states")
public class WorkflowState {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String name;

    private String color;

    private Integer isFinal;

    private Integer sortOrder;
}
