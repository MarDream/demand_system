package com.demand.system.module.iteration.dto;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class IterationVO {

    private Long id;

    private Long projectId;

    private String name;

    private String description;

    private LocalDate startDate;

    private LocalDate endDate;

    private BigDecimal capacity;

    private String status;

    private Long creatorId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer requirementCount;

    private Integer completedCount;

    private Double progress;
}
