package com.demand.system.module.requirement.dto;

import lombok.Data;

import java.util.List;

@Data
public class RequirementFormConfigDTO {

    private String defaultTypeCode;

    private String defaultTypeName;

    private String defaultTypeColor;

    private List<String> visibleFields;

    private List<String> requiredFields;
}
