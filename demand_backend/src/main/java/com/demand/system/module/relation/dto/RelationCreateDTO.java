package com.demand.system.module.relation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RelationCreateDTO {

    @NotNull(message = "目标需求ID不能为空")
    private Long targetId;

    @NotBlank(message = "关联类型不能为空")
    private String relationType;
}
