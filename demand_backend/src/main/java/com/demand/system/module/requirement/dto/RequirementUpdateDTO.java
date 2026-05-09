package com.demand.system.module.requirement.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class RequirementUpdateDTO {

    @NotNull(message = "需求ID不能为空")
    private Long id;

    private String title;

    private String description;

    private String type;

    private String priority;

    private Long assigneeId;

    private Long iterationId;

    private Long moduleId;

    private LocalDate startDate;

    private LocalDate dueDate;

    private BigDecimal estimatedHours;

    private BigDecimal actualHours;

    private List<RequirementAttachmentDTO> attachments;

    private String status;

    private Integer orderNum;
}
