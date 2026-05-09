package com.demand.system.module.llm.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class LlmProviderDTO {
    @NotBlank(message = "名称不能为空")
    private String name;
    @NotBlank(message = "协议类型不能为空")
    private String protocol;
    @NotBlank(message = "API Base URL不能为空")
    private String baseUrl;
    private String apiKey;
    private Boolean enabled = true;
}
