package com.demand.system.module.requirement.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public class RequirementApprovalSupplementCreateDTO {

    @NotBlank(message = "补充意见不能为空")
    private String content;

    private List<RequirementAttachmentDTO> attachments;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<RequirementAttachmentDTO> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<RequirementAttachmentDTO> attachments) {
        this.attachments = attachments;
    }
}
