package com.demand.system.module.workflow.dto;

import java.util.List;

public class WorkflowAvailableActionsDTO {

    private Boolean canTransition;

    private Boolean canRollback;

    private Boolean canCancel;

    private List<AvailableTransitionDTO> transitions;

    public Boolean getCanTransition() {
        return canTransition;
    }

    public void setCanTransition(Boolean canTransition) {
        this.canTransition = canTransition;
    }

    public Boolean getCanRollback() {
        return canRollback;
    }

    public void setCanRollback(Boolean canRollback) {
        this.canRollback = canRollback;
    }

    public Boolean getCanCancel() {
        return canCancel;
    }

    public void setCanCancel(Boolean canCancel) {
        this.canCancel = canCancel;
    }

    public List<AvailableTransitionDTO> getTransitions() {
        return transitions;
    }

    public void setTransitions(List<AvailableTransitionDTO> transitions) {
        this.transitions = transitions;
    }
}
