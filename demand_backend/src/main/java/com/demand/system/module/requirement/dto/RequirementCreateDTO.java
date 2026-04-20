package com.demand.system.module.requirement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RequirementCreateDTO {

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    private Long parentId;

    @NotBlank(message = "标题不能为空")
    private String title;

    private String description;

    @NotBlank(message = "需求类型不能为空")
    private String type;

    @NotBlank(message = "优先级不能为空")
    private String priority;

    private Long assigneeId;

    private Long iterationId;

    private Long moduleId;

    private LocalDate dueDate;

    private BigDecimal estimatedHours;
}
