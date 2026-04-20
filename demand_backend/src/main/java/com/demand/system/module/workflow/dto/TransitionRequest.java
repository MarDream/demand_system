package com.demand.system.module.workflow.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TransitionRequest {

    @NotNull(message = "目标状态不能为空")
    private Long targetStateId;

    private String comment;

    private String fieldValues;
}
