package com.demand.system.module.project.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ProjectCreateDTO {

    @NotBlank(message = "项目名称不能为空")
    private String name;

    private String description;

    private Long companyId;

    private String team;

    private Long leaderId;

    private LocalDate startDate;

    private LocalDate endDate;
}
