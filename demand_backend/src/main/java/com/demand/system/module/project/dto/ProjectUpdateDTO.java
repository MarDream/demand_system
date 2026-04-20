package com.demand.system.module.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProjectUpdateDTO {

    @NotNull(message = "项目ID不能为空")
    private Long id;

    @NotBlank(message = "项目名称不能为空")
    private String name;

    private String description;

    private String status;
}
