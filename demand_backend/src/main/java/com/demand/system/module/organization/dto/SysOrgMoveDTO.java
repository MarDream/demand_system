package com.demand.system.module.organization.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SysOrgMoveDTO {

    @NotNull(message = "节点ID不能为空")
    private Long id;

    private Long targetParentId;

    private Integer targetSortOrder;
}
