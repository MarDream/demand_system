package com.demand.system.module.workflow.dto;

import lombok.Data;

import java.util.List;

@Data
public class NodeConfigDTO {

    private String nodeId;

    private String name;

    private String type;

    private String color;

    private Boolean isFinal;

    private Integer sortOrder;

    private List<String> allowedRoles;

    private List<Long> allowedUsers;

    private List<String> editableFields;

    private List<String> requiredFields;

    private List<String> availableActions;
}
