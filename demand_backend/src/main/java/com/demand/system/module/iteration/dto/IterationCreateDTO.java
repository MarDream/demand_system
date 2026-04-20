package com.demand.system.module.iteration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class IterationCreateDTO {

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    @NotBlank(message = "迭代名称不能为空")
    private String name;

    private String description;

    @NotNull(message = "开始日期不能为空")
    private LocalDate startDate;

    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;

    private BigDecimal capacity;
}
