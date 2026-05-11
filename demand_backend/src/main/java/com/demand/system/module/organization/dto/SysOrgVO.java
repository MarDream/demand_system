package com.demand.system.module.organization.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class SysOrgVO {

    private Long id;

    private String name;

    private Long parentId;

    private String orgType;

    private String code;

    private Long leaderId;

    private String leaderName;

    private String description;

    private Integer sortOrder;

    private String path;

    private Integer level;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private List<SysOrgVO> children;
}
