package com.demand.system.module.llm.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LlmTestResultVO {
    private boolean success;
    private String content;
    private String errorMessage;
    private long durationMs;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private String model;
}
