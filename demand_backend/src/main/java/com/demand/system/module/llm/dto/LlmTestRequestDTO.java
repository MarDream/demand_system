package com.demand.system.module.llm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LlmTestRequestDTO {
    @NotBlank(message = "测试消息不能为空")
    private String userMessage;
    private String systemPrompt;
}
