package com.demand.system.module.workflow.dto;

import lombok.Data;

import java.util.List;

@Data
public class WorkflowConfigDTO {

    private List<WorkflowNodeDTO> nodes;

    private List<WorkflowEdgeDTO> edges;
}
