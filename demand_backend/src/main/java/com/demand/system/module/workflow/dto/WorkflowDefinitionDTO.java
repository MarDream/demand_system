package com.demand.system.module.workflow.dto;

import lombok.Data;

import java.util.List;

@Data
public class WorkflowDefinitionDTO {

    private Long id;

    private String name;

    private List<NodeConfigDTO> nodes;

    private List<EdgeDTO> edges;
}
