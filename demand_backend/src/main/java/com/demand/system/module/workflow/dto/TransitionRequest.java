package com.demand.system.module.workflow.dto;

import jakarta.validation.constraints.NotNull;

public class TransitionRequest {

    @NotNull(message = "目标状态不能为空")
    private Long targetStateId;

    private String comment;

    private String fieldValues;

    public Long getTargetStateId() {
        return targetStateId;
    }

    public void setTargetStateId(Long targetStateId) {
        this.targetStateId = targetStateId;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getFieldValues() {
        return fieldValues;
    }

    public void setFieldValues(String fieldValues) {
        this.fieldValues = fieldValues;
    }
}
