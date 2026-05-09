package com.demand.system.module.rbac.dto;

import lombok.Data;

@Data
public class MenuSortItem {
    private Long id;
    private Long parentId;
    private Integer sortOrder;
}
