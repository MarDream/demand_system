package com.demand.system.module.workflow.dto;

import lombok.Data;

@Data
public class AvailableTransitionDTO {

    private String toNodeId;

    private String toNodeName;

    private String label;
}
