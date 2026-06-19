package com.demand.system.module.workflow.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public class TransitionRequest {

    @NotNull(message = "目标状态不能为空")
    private Long targetStateId;

    private String comment;

    private String fieldValues;

    /** 流转时携带的附件ID列表（来自 file_records.id）。 */
    private List<Long> attachmentIds;

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

    public List<Long> getAttachmentIds() {
        return attachmentIds;
    }

    public void setAttachmentIds(List<Long> attachmentIds) {
        this.attachmentIds = attachmentIds;
    }
}
