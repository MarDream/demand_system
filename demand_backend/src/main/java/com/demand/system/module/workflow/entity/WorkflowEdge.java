package com.demand.system.module.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@TableName(value = "workflow_edges", autoResultMap = true)
public class WorkflowEdge {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workflowVersionId;

    private String edgeId;

    private String sourceNodeId;

    private String targetNodeId;

    private String label;

    @com.baomidou.mybatisplus.annotation.TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> condition;

    @com.baomidou.mybatisplus.annotation.TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> properties;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
