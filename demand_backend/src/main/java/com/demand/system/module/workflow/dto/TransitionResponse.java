package com.demand.system.module.workflow.dto;

import com.demand.system.module.workflow.entity.WorkflowTransition;

import java.util.List;

public class TransitionResponse {

    private Boolean success;

    private String newStatus;

    private List<WorkflowTransition> availableTransitions;

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(String newStatus) {
        this.newStatus = newStatus;
    }

    public List<WorkflowTransition> getAvailableTransitions() {
        return availableTransitions;
    }

    public void setAvailableTransitions(List<WorkflowTransition> availableTransitions) {
        this.availableTransitions = availableTransitions;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Boolean success;
        private String newStatus;
        private List<WorkflowTransition> availableTransitions;

        public Builder success(Boolean success) {
            this.success = success;
            return this;
        }

        public Builder newStatus(String newStatus) {
            this.newStatus = newStatus;
            return this;
        }

        public Builder availableTransitions(List<WorkflowTransition> availableTransitions) {
            this.availableTransitions = availableTransitions;
            return this;
        }

        public TransitionResponse build() {
            TransitionResponse response = new TransitionResponse();
            response.success = this.success;
            response.newStatus = this.newStatus;
            response.availableTransitions = this.availableTransitions;
            return response;
        }
    }
}
