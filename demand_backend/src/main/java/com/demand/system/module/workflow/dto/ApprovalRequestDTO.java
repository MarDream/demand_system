package com.demand.system.module.workflow.dto;

import java.util.List;

public class ApprovalRequestDTO {

    private String comment;

    /** 审批时携带的附件ID列表（来自 file_records.id）。 */
    private List<Long> attachmentIds;

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public List<Long> getAttachmentIds() {
        return attachmentIds;
    }

    public void setAttachmentIds(List<Long> attachmentIds) {
        this.attachmentIds = attachmentIds;
    }
}
