package com.demand.system.module.requirement.dto;

import jakarta.validation.constraints.NotBlank;

public class RequirementApprovalSupplementCreateDTO {

    @NotBlank(message = "补充意见不能为空")
    private String content;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
