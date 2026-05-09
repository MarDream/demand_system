package com.demand.system.module.workflow.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class WorkflowNodeDTO {

    private String nodeId;

    private String nodeType;

    private String nodeName;

    private Integer positionX;

    private Integer positionY;

    private String assigneeType;

    private Integer assigneeRoleId;

    private List<Long> assigneeUserIds;

    private Integer timeoutHours;

    private String timeoutAction;

    private Map<String, Object> properties;
}
