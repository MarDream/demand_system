package com.demand.system.module.organization.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PositionVO {

    private Long id;

    private String name;

    private String code;

    private String level;

    private String description;

    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
