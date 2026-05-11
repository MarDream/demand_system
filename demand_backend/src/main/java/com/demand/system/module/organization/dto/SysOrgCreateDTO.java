package com.demand.system.module.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SysOrgCreateDTO {

    @NotBlank(message = "组织名称不能为空")
    @Size(max = 100, message = "组织名称不能超过100个字符")
    private String name;

    private Long parentId;

    @NotBlank(message = "组织类型不能为空")
    private String orgType;

    private String code;

    private Long leaderId;

    private String description;

    private Integer sortOrder;
}
