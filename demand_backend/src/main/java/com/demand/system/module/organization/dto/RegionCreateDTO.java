package com.demand.system.module.organization.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegionCreateDTO {

    @NotBlank(message = "区域名称不能为空")
    private String name;

    private Long parentId;

    private String code;

    private String description;

    private Integer sortOrder;
}
