package com.demand.system.module.workflow.dto;

import com.demand.system.module.workflow.entity.WorkflowTransition;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TransitionResponse {

    private Boolean success;

    private String newStatus;

    private List<WorkflowTransition> availableTransitions;
}
