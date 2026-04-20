package com.demand.system.module.workflow.dto;

import lombok.Data;

@Data
public class EdgeDTO {

    private String source;

    private String target;

    private String label;
}
