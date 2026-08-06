package com.demand.system.module.assistant.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 重新生成某条助手回复的请求。
 * <p>复用 {@link AssistantChatRequest} 的全部参数（问题文本、知识库范围、模型等），
 * 额外携带需要重新生成的助手消息 ID。</p>
 */
public class AssistantRegenerateRequest extends AssistantChatRequest {

    /** 需要重新生成的助手消息 ID */
    @NotNull(message = "assistantMessageId 不能为空")
    private Long assistantMessageId;

    public Long getAssistantMessageId() {
        return assistantMessageId;
    }

    public void setAssistantMessageId(Long assistantMessageId) {
        this.assistantMessageId = assistantMessageId;
    }
}
