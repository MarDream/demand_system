package com.demand.system.module.workflow.dto;

import java.util.List;

public class WorkflowConfigDTO {

    private List<WorkflowNodeDTO> nodes;

    private List<WorkflowEdgeDTO> edges;

    public List<WorkflowNodeDTO> getNodes() {
        return nodes;
    }

    public void setNodes(List<WorkflowNodeDTO> nodes) {
        this.nodes = nodes;
    }

    public List<WorkflowEdgeDTO> getEdges() {
        return edges;
    }

    public void setEdges(List<WorkflowEdgeDTO> edges) {
        this.edges = edges;
    }
}
