package com.demand.system.module.workflow.dto;

import java.util.List;

public class WorkflowDefinitionDTO {

    private Long id;

    private String processKey;

    private String name;

    private List<NodeConfigDTO> nodes;

    private List<EdgeDTO> edges;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProcessKey() {
        return processKey;
    }

    public void setProcessKey(String processKey) {
        this.processKey = processKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<NodeConfigDTO> getNodes() {
        return nodes;
    }

    public void setNodes(List<NodeConfigDTO> nodes) {
        this.nodes = nodes;
    }

    public List<EdgeDTO> getEdges() {
        return edges;
    }

    public void setEdges(List<EdgeDTO> edges) {
        this.edges = edges;
    }
}
