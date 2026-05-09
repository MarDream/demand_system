package com.demand.system.module.llm.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class LlmProviderVO {
    private Long id;
    private String name;
    private String protocol;
    private String baseUrl;
    private String maskedApiKey;
    private Boolean enabled;
    private List<LlmModelVO> models;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
