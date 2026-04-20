package com.demand.system.module.requirement.dto;

import lombok.Data;

@Data
public class RequirementQueryDTO {

    private Long projectId;

    private Long parentId;

    private String type;

    private String priority;

    private String status;

    private Long assigneeId;

    private Long iterationId;

    private String keyword;

    private int pageNum = 1;

    private int pageSize = 10;

    private String sortField;

    private String sortOrder;
}
