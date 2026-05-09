package com.demand.system.module.llm.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class LlmModelVO {
    private Long id;
    private Long providerId;
    private String name;
    private String modelId;
    private String modelType;
    private BigDecimal temperature;
    private Integer maxTokens;
    private Boolean isDefault;
    private Boolean enabled;
    private Boolean testSuccess;
    private Integer testDuration;
    private String testError;
    private LocalDateTime testAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
