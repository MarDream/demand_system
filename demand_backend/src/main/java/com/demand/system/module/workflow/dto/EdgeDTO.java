package com.demand.system.module.workflow.dto;

import lombok.Data;

import java.util.List;

@Data
public class EdgeDTO {

    private String source;

    private String target;

    private String label;

    private List<String> allowedRoles;

    private List<String> requiredFields;

    private String conditions;

    private Boolean defaultFlow;
}
