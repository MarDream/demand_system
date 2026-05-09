package com.demand.system.module.organization.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class DepartmentVO {

    private Long id;

    private String name;

    private Long parentId;

    private Long regionId;

    private String regionName;

    private Long leaderId;

    private String leaderName;

    private String code;

    private String type;

    private String description;

    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private List<DepartmentVO> children;
}
