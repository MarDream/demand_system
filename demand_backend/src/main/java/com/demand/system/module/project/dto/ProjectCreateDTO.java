package com.demand.system.module.project.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProjectCreateDTO {

    @NotBlank(message = "项目名称不能为空")
    private String name;

    private String description;
}
