package com.demand.system.module.organization.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PositionCreateDTO {

    @NotBlank(message = "岗位名称不能为空")
    private String name;

    private String code;

    private String level;

    private String description;

    private Integer sortOrder;
}
