package com.demand.system.module.organization.dto;

import lombok.Data;

@Data
public class DepartmentQueryDTO {

    private Long regionId;

    private String name;

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}
