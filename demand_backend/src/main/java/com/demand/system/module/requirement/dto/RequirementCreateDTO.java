package com.demand.system.module.requirement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class RequirementCreateDTO {

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    private Long parentId;

    @NotBlank(message = "标题不能为空")
    private String title;

    private String description;

    private String type;

    @NotBlank(message = "优先级不能为空")
    private String priority;

    private Long assigneeId;

    private Long iterationId;

    private Long moduleId;

    private LocalDate startDate;

    private LocalDate dueDate;

    private BigDecimal estimatedHours;

    private List<RequirementAttachmentDTO> attachments;
}
