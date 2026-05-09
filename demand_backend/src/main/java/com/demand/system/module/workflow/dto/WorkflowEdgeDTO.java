package com.demand.system.module.workflow.dto;

import lombok.Data;

import java.util.Map;

@Data
public class WorkflowEdgeDTO {

    private String edgeId;

    private String sourceNodeId;

    private String targetNodeId;

    private String label;

    private Map<String, Object> condition;

    private Map<String, Object> properties;
}
