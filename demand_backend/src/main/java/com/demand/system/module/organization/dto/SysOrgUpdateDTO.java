package com.demand.system.module.organization.dto;

import lombok.Data;

@Data
public class SysOrgUpdateDTO {

    private Long id;

    private String name;

    private Long parentId;

    private String orgType;

    private String code;

    private Long leaderId;

    private String description;

    private Integer sortOrder;
}
