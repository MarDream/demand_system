package com.demand.system.module.llm.dto;

import jakarta.validation.constraints.NotBlank;

public class LlmTestRequestDTO {
    @NotBlank(message = "测试消息不能为空")
    private String userMessage;
    private String systemPrompt;

    public String getUserMessage() { return userMessage; }
    public void setUserMessage(String userMessage) { this.userMessage = userMessage; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
}
