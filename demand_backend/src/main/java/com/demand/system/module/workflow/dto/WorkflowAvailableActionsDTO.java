package com.demand.system.module.workflow.dto;

import lombok.Data;

import java.util.List;

@Data
public class WorkflowAvailableActionsDTO {

    private Boolean canTransition;

    private Boolean canRollback;

    private Boolean canCancel;

    private List<AvailableTransitionDTO> transitions;
}
