package com.demand.system.module.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AssistantChatRequest {

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 4000, message = "消息内容长度不能超过 4000")
    private String message;

    private AssistantPageContext pageContext;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public AssistantPageContext getPageContext() {
        return pageContext;
    }

    public void setPageContext(AssistantPageContext pageContext) {
        this.pageContext = pageContext;
    }
}
