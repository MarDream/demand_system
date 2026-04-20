package com.demand.system.module.relation.dto;

import lombok.Data;

@Data
public class RelationVO {

    private Long id;

    private Long sourceId;

    private Long targetId;

    private String relationType;

    private String targetTitle;

    private String targetStatus;

    private String targetPriority;
}
