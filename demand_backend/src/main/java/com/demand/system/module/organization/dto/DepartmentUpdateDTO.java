package com.demand.system.module.organization.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DepartmentUpdateDTO {

    private Long id;

    @NotBlank(message = "部门名称不能为空")
    private String name;

    private Long parentId;

    private Long regionId;

    private Long leaderId;

    private String code;

    private String type;

    private String description;

    private Integer sortOrder;
}
