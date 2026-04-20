package com.demand.system.module.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("workflow_versions")
public class WorkflowVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private Integer version;

    private String name;

    private String definition;

    private Integer isActive;

    private Long creatorId;

    private LocalDateTime createdAt;
}
