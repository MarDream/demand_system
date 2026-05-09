package com.demand.system.module.organization.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class RegionVO {

    private Long id;

    private String name;

    private Long parentId;

    private String code;

    private String description;

    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private List<RegionVO> children;
}
