package com.demand.system.module.iteration.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class IterationUpdateDTO {

    @NotNull(message = "迭代ID不能为空")
    private Long id;

    private String name;

    private String description;

    private LocalDate startDate;

    private LocalDate endDate;

    private BigDecimal capacity;

    private String status;
}
