package com.demand.system.module.workflow.dto;

import lombok.Data;

import java.util.List;

@Data
public class NodeConfigDTO {

    private String nodeId;

    private String name;

    private String type;

    private List<String> allowedRoles;

    private List<String> editableFields;

    private List<String> requiredFields;

    private List<String> availableActions;
}
