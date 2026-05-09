package com.demand.system.module.workflow.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FlowTransitionRequest {

    @NotNull(message = "需求ID不能为空")
    private Long requirementId;

    @NotNull(message = "目标节点ID不能为空")
    private String toNodeId;

    private String action;

    private String comment;
}
