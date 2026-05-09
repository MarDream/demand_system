package com.demand.system.module.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@TableName(value = "workflow_nodes", autoResultMap = true)
public class WorkflowNode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workflowVersionId;

    private String nodeId;

    private String nodeType;

    private String nodeName;

    private Integer positionX;

    private Integer positionY;

    private String assigneeType;

    private Integer assigneeRoleId;

    @com.baomidou.mybatisplus.annotation.TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> assigneeUserIds;

    private Integer timeoutHours;

    private String timeoutAction;

    @com.baomidou.mybatisplus.annotation.TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> properties;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
