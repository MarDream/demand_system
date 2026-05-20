package com.demand.system.module.workflow.dto;

import jakarta.validation.constraints.NotNull;

public class WorkflowVersionActivationDTO {

    @NotNull(message = "启停状态不能为空")
    private Boolean active;

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
