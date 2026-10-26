package com.demand.system.module.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 智能体写操作工具的用户确认回填请求 */
public class AssistantToolConfirmDTO {

    @NotBlank(message = "requestId 不能为空")
    private String requestId;

    @NotNull(message = "approved 不能为空")
    private Boolean approved;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Boolean getApproved() {
        return approved;
    }

    public void setApproved(Boolean approved) {
        this.approved = approved;
    }
}
